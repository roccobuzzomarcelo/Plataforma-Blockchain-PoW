<#
.SYNOPSIS
  Pruebas de la seccion 3.3: mide tiempos de minado bajo distintas
  configuraciones sobre el cluster real y guarda todo en un CSV.

.DESCRIPTION
  Ubicacion: pilar3-infra/test-load/run_experiments.ps1
  Compatible con Windows PowerShell 5.1 y PowerShell 7. Solo ASCII.

  Que hace:
    1. (salvo -NoClusterConfig) configura el transaction-pool del cluster:
       BLOCK_INTERVAL=3600 (apaga el scheduler de 60 s, para que ningun
       ciclo automatico procese transacciones de una prueba) y
       POOL_TEST_ENDPOINTS=true (habilita /api/pool/test/*).
    2. Abre sus propios port-forward, en 127.0.0.1 y en puertos poco
       comunes (18082 / 18080), y verifica que apuntan al cluster.
    3. Recorre los escenarios de scenarios/*.json, repitiendo cada
       configuracion N veces, y agrega una fila al CSV por cada corrida.
    4. Al terminar (o con Ctrl+C) cierra los tuneles y restaura la
       configuracion del pool.

  Uso:
    .\run_experiments.ps1 -Experiment prefix
    .\run_experiments.ps1 -Experiment all -Label vm_off
    .\run_experiments.ps1 -Experiment bulk -IncludeOptional

  Experimentos: prefix | fragmentation | bulk | gpu | all
  El nombre -Label identifica la corrida en los graficos (por ejemplo
  vm_off / vm_on para comparar con y sin la VM minera externa).
#>
param(
    [ValidateSet('prefix', 'fragmentation', 'bulk', 'gpu', 'all')]
    [string]$Experiment = 'prefix',
    [string]$Label = 'base',
    [string]$OutDir = '',
    [int]$Repetitions = 0,
    [switch]$IncludeOptional,
    [string]$Namespace = 'blockchain-apps',
    [string]$InfraNamespace = 'blockchain-infra',
    [string]$RedisPass = 'redis123',
    [string]$PoolUrl = '',
    [string]$ApiUrl = '',
    [string]$CoordUrl = '',
    [int]$PoolPort = 18082,
    [int]$ApiPort = 18080,
    [int]$CoordPort = 18081,
    [switch]$NoClusterConfig,
    [string]$ScenarioDir = '',
    [int]$PollMs = 100,
    [int]$MinCooldownSec = 2,
    # A partir de esta cantidad de chunks en una corrida, se hace el
    # reset completo (workers a 0, delete_queue, workers de vuelta)
    # entre repeticiones en vez de un simple purge -ver el comentario
    # en Invoke-OneRun.
    [int]$HardResetChunkThreshold = 10,
    [string]$GcpProject = 'sdypp-rocco',
    [string]$GcpZone = 'us-central1-a',
    [string]$MigName = 'external-miner-mig',
    [switch]$NoExternalMiner
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$Inv = [System.Globalization.CultureInfo]::InvariantCulture
if ($ScenarioDir -eq '') { $ScenarioDir = Join-Path $PSScriptRoot 'scenarios' }

$script:Tunnels = @()
$script:EnvChanged = $false
$script:PoolBase = ''
$script:ApiBase = ''
$script:CoordBase = ''

# ---------------------------------------------------------------- utilidades
function Write-Log([string]$Msg) {
    Write-Host ('[' + (Get-Date).ToString('HH:mm:ss', $Inv) + '] ' + $Msg)
}

# Numero -> texto con punto decimal (el locale es-AR usa coma y rompe el CSV)
function Num($x) {
    if ($null -eq $x) { return '' }
    if ($x -is [string] -and $x -eq '') { return '' }
    return ([double]$x).ToString('0.###', $Inv)
}

function Get-Prop($Obj, [string]$Name, $Default) {
    if ($null -ne $Obj -and $Obj.PSObject.Properties.Match($Name).Count -gt 0) { return $Obj.$Name }
    return $Default
}

function Invoke-Kubectl {
    param([string[]]$KArgs)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & kubectl @KArgs 2>$null } finally { $ErrorActionPreference = $old }
    if ($null -eq $out) { return @() }
    return @($out)
}

function Invoke-Gcloud {
    param([string[]]$GArgs)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & gcloud @GArgs 2>$null } finally { $ErrorActionPreference = $old }
    if ($null -eq $out) { return @() }
    return @($out)
}

function Http-Get([string]$Url, [int]$TimeoutSec = 30) {
    return Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec $TimeoutSec
}

function Http-Post([string]$Url, [string]$Body = '', [int]$TimeoutSec = 60) {
    return Invoke-RestMethod -Uri $Url -Method Post -Body $Body -ContentType 'application/json' -TimeoutSec $TimeoutSec
}

# ------------------------------------------------------- cluster y tuneles
# No confia en la ultima linea capturada de "kubectl rollout status": con el
# cluster ocupado (reordenando nodos tras escalar la VM externa, por
# ejemplo) el rollout puede tardar bastante mas de lo habitual, y la
# ultima linea vista puede seguir siendo un "...pending termination..."
# en vez de la confirmacion real. Se exige ver "successfully rolled out"
# de forma explicita, reintentando el status si hace falta.
function Wait-Rollout([int]$TimeoutSec = 300) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $remaining = [int]([Math]::Max(10, ($deadline - (Get-Date)).TotalSeconds))
        $st = Invoke-Kubectl @('rollout', 'status', 'deployment/transaction-pool', '-n', $Namespace, ('--timeout=' + $remaining + 's'))
        $last = ($st | Select-Object -Last 1)
        Write-Log ('  ' + $last)
        if ($last -match 'successfully rolled out') { return }
    }
    throw 'El rollout de transaction-pool no confirmo "successfully rolled out" dentro del tiempo esperado.'
}

function Set-PoolConfig([hashtable]$Vars) {
    $kargs = @('set', 'env', 'deployment/transaction-pool', '-n', $Namespace)
    foreach ($k in $Vars.Keys) { $kargs += ($k + '=' + $Vars[$k]) }
    Write-Log ('Configurando transaction-pool: ' + (($Vars.Keys | ForEach-Object { $_ + '=' + $Vars[$_] }) -join ' '))
    Invoke-Kubectl $kargs | Out-Null
    $script:EnvChanged = $true
    Wait-Rollout
}

function Restore-PoolConfig([string[]]$Keys) {
    $kargs = @('set', 'env', 'deployment/transaction-pool', '-n', $Namespace)
    foreach ($k in $Keys) { $kargs += ($k + '-') }
    Write-Log 'Restaurando la configuracion original del transaction-pool...'
    Invoke-Kubectl $kargs | Out-Null
    Wait-Rollout
}

function Start-Tunnel([string]$Svc, [int]$LocalPort, [int]$RemotePort) {
    $tmp = [System.IO.Path]::GetTempPath()
    $sp = @{
        FilePath               = 'kubectl'
        ArgumentList           = @('port-forward', '-n', $Namespace, ('svc/' + $Svc), '--address', '127.0.0.1', ([string]$LocalPort + ':' + [string]$RemotePort))
        PassThru               = $true
        RedirectStandardOutput = (Join-Path $tmp ('pf-' + $Svc + '-' + $LocalPort + '.out'))
        RedirectStandardError  = (Join-Path $tmp ('pf-' + $Svc + '-' + $LocalPort + '.err'))
    }
    if ($PSVersionTable.PSVersion.Major -lt 6 -or $IsWindows) { $sp['WindowStyle'] = 'Hidden' }
    $p = Start-Process @sp
    $script:Tunnels += $p
    Write-Log ('Tunel svc/' + $Svc + ' -> 127.0.0.1:' + $LocalPort + ' (pid ' + $p.Id + ')')
}

function Stop-Tunnels {
    foreach ($p in $script:Tunnels) {
        try { if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force } } catch { }
    }
    if ($script:Tunnels.Count -gt 0) { Write-Log 'Tuneles cerrados.' }
    $script:Tunnels = @()
}

function Wait-Url([string]$Url, [int]$TimeoutSec = 40) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try { Http-Get $Url 5 | Out-Null; return $true } catch { Start-Sleep -Milliseconds 500 }
    }
    return $false
}

function Get-WorkerCount {
    $out = Invoke-Kubectl @('exec', '-n', $InfraNamespace, 'rabbitmq-0', '--', 'rabbitmqctl', 'list_queues', 'name', 'consumers')
    foreach ($l in $out) {
        if (([string]$l) -match '^mining\.tasks\s+(\d+)') { return [int]$Matches[1] }
    }
    return 0
}

function Reset-Queue {
    Invoke-Kubectl @('exec', '-n', $InfraNamespace, 'rabbitmq-0', '--', 'rabbitmqctl', 'purge_queue', 'mining.tasks') | Out-Null
}

function Get-MigSize {
    if ($NoExternalMiner) { return 0 }
    $out = Invoke-Gcloud @('compute', 'instance-groups', 'managed', 'describe', $MigName,
        '--zone', $GcpZone, '--project', $GcpProject, '--format=value(targetSize)')
    $line = $out | Select-Object -First 1
    if ($line -match '^\d+$') { return [int]$line }
    return 0
}

function Get-WorkerReplicas {
    $out = Invoke-Kubectl @('get', 'deployment', 'worker', '-n', $Namespace, '-o', 'jsonpath={.spec.replicas}')
    $line = ($out -join '').Trim()
    if ($line -match '^\d+$') { return [int]$line }
    return 2
}

# purge_queue solo borra lo que espera en la cola, no lo que un
# consumidor ya tiene "en vuelo" -por eso un simple purge no alcanza
# para limpiar chunks perdedores todavia en ejecucion. Este reset baja
# a CERO todo lo que puede tener un chunk agarrado -los pods Y la VM
# externa, si esta activa-, borra la cola completa (no solo purge), y
# recien ahi reconecta todo al tamano que tenia antes.
function Invoke-HardReset {
    Write-Log 'Reset completo: bajando workers y VM externa para limpiar trabajo en curso...'
    $workerReplicas = Get-WorkerReplicas
    $migSize = Get-MigSize

    Invoke-Kubectl @('scale', 'deployment/worker', '-n', $Namespace, '--replicas=0') | Out-Null
    if (-not $NoExternalMiner -and $migSize -gt 0) {
        Invoke-Gcloud @('compute', 'instance-groups', 'managed', 'resize', $MigName,
            '--zone', $GcpZone, '--project', $GcpProject, '--size=0') | Out-Null
    }
    Start-Sleep -Seconds 20

    Invoke-Kubectl @('exec', '-n', $InfraNamespace, 'rabbitmq-0', '--', 'rabbitmqctl', 'delete_queue', 'mining.tasks') | Out-Null

    Invoke-Kubectl @('scale', 'deployment/worker', '-n', $Namespace, "--replicas=$workerReplicas") | Out-Null
    Invoke-Kubectl @('rollout', 'status', 'deployment/worker', '-n', $Namespace, '--timeout=300s') | Out-Null
    if (-not $NoExternalMiner -and $migSize -gt 0) {
        Invoke-Gcloud @('compute', 'instance-groups', 'managed', 'resize', $MigName,
            '--zone', $GcpZone, '--project', $GcpProject, "--size=$migSize") | Out-Null
        Write-Log '  esperando a que la VM minera externa vuelva a levantar (hasta 3 min)...'
        $deadline = (Get-Date).AddSeconds(180)
        while ((Get-Date) -lt $deadline) {
            if ((Get-WorkerCount) -ge ($workerReplicas + $migSize)) { break }
            Start-Sleep -Seconds 10
        }
    }
    Write-Log ('  reset listo, consumidores en mining.tasks: ' + (Get-WorkerCount))
}

# Comprueba que la API a la que apuntamos es la del cluster: la cantidad de
# bloques que informa debe coincidir con el SCARD del Redis del cluster.
function Test-Target {
    $stats = Http-Get ($script:ApiBase + '/api/chain/stats')
    $out = Invoke-Kubectl @('exec', '-n', $InfraNamespace, 'redis-0', '--', 'redis-cli', '-a', $RedisPass, '--no-auth-warning', 'SCARD', 'blockchain:blocks')
    $n = $null
    foreach ($l in $out) { if (([string]$l) -match '^\s*(\d+)\s*$') { $n = [int]$Matches[1] } }
    if ($null -eq $n) {
        Write-Log 'AVISO: no pude leer Redis por kubectl; no se verifica el destino.'
        return
    }
    if ($n -ne [int]$stats.totalBlocks) {
        throw ('El destino NO es el cluster: la API dice ' + $stats.totalBlocks + ' bloques y el Redis del cluster tiene ' + $n + '.')
    }
    Write-Log ('Destino verificado: la API y el Redis del cluster coinciden (' + $n + ' bloques).')
}

# Detecta imagenes viejas: un pool sin los endpoints de prueba, o un
# coordinator que ignoraria chunkCount/rangeSize en silencio, harian que
# se midieran configuraciones distintas de las pedidas sin ningun error.
function Test-Capabilities {
    try { Http-Post ($script:PoolBase + '/api/pool/test/clear') '' 60 | Out-Null }
    catch {
        throw 'El transaction-pool no tiene los endpoints de prueba (/api/pool/test/*): falta reconstruir/desplegar la imagen del pool, o POOL_TEST_ENDPOINTS no esta en true.'
    }
    if ($script:CoordBase -eq '') {
        Write-Log 'AVISO: sin URL del coordinator; no se verifica que soporte chunkCount/rangeSize por pedido.'
        return
    }
    $cs = Http-Get ($script:CoordBase + '/api/coordinator/status')
    if (-not [bool](Get-Prop $cs 'supportsOverrides' $false)) {
        throw 'El coordinator no soporta chunkCount/rangeSize por pedido: falta reconstruir/desplegar la imagen del coordinator.'
    }
    Write-Log 'Capacidades verificadas: pool con endpoints de prueba y coordinator con overrides.'
}

# ------------------------------------------------------------- una corrida
function Send-Transactions([int]$N, [string]$Mode) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $serverMs = ''
    if ($Mode -eq 'single') {
        for ($i = 1; $i -le $N; $i++) {
            $body = '{"sender":"L' + $i + '","receiver":"M' + $i + '","amount":' + $i + '}'
            Http-Post ($script:ApiBase + '/api/transactions') $body 60 | Out-Null
        }
    }
    else {
        $r = Http-Post ($script:PoolBase + '/api/pool/test/generate?count=' + $N) '' 3600
        $serverMs = $r.ms
    }
    $sw.Stop()
    return @{ Ms = $sw.Elapsed.TotalMilliseconds; ServerMs = $serverMs }
}

function Get-BlockInfo([int]$Index, [int]$ExpectedTx) {
    $info = @{ Nonce = ''; Winner = ''; TxInBlock = ''; PrefixLen = ''; Note = '' }
    try {
        $r = Invoke-WebRequest -Uri ($script:ApiBase + '/api/chain/blocks/' + $Index) -UseBasicParsing -TimeoutSec 300
        $raw = [string]$r.Content
        $m = [regex]::Match($raw, '"nonce"\s*:\s*(\d+)')
        if ($m.Success) { $info.Nonce = $m.Groups[1].Value }
        $m = [regex]::Match($raw, '"prefix"\s*:\s*"(0*)"')
        if ($m.Success) { $info.PrefixLen = $m.Groups[1].Value.Length }
        $m = [regex]::Match($raw, '"receiver"\s*:\s*"([^"]+)"[^{}]*?"type"\s*:\s*"COINBASE"')
        if ($m.Success) { $info.Winner = $m.Groups[1].Value }
        $info.TxInBlock = [regex]::Matches($raw, '"type"\s*:\s*"TRANSFER"').Count
        if ($info.TxInBlock -ne $ExpectedTx) {
            $info.Note = 'AVISO: el bloque tiene ' + $info.TxInBlock + ' transferencias y se esperaban ' + $ExpectedTx
        }
    }
    catch { $info.Note = 'no se pudo leer el bloque: ' + $_.Exception.Message }
    return $info
}

function Invoke-OneRun([hashtable]$Cfg) {
    $workers = Get-WorkerCount
    if (([string]$Cfg.chunks) -eq 'auto') { $chunks = [Math]::Max(1, $workers) } else { $chunks = [int]$Cfg.chunks }
    $range = [long]$Cfg.range
    $txCount = [int]$Cfg.txCount
    $note = ''
    $status = 'OK'
    $ingest = @{ Ms = ''; ServerMs = '' }
    $flushMs = ''
    $totalMs = ''
    $blockIndex = ''
    $info = @{ Nonce = ''; Winner = ''; TxInBlock = ''; PrefixLen = ''; Note = '' }

    try {
        # 1. pool vacio de partida
        $st = Http-Get ($script:PoolBase + '/api/pool/status')
        if ([int]$st.pendingTransactions -gt 0) {
            Http-Post ($script:PoolBase + '/api/pool/test/clear') '' 600 | Out-Null
            $note += 'pool no estaba vacio, se limpio; '
        }

        # 2. cargar transacciones
        $ingest = Send-Transactions $txCount $Cfg.ingest
        $st = Http-Get ($script:PoolBase + '/api/pool/status')
        if ([int]$st.pendingTransactions -ne $txCount) {
            $note += 'pendientes=' + $st.pendingTransactions + ' (esperadas ' + $txCount + '); '
        }
        $startIdx = [int](Http-Get ($script:ApiBase + '/api/chain/stats')).latestBlockIndex

        # 3. flush con los parametros de la corrida y espera del bloque
        $q = @()
        if ($Cfg.prefix) { $q += ('prefix=' + $Cfg.prefix) }
        $q += ('chunks=' + $chunks)
        $q += ('range=' + $range)
        $url = $script:PoolBase + '/api/pool/flush?' + ($q -join '&')

        if ($Cfg.keepAlive) {
            Http-Post ($script:PoolBase + '/api/pool/miners/keepalive') '{"workerId":"gpu-sim-1"}' 10 | Out-Null
        }
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $resp = [string](Http-Post $url '' 900)
        $flushMs = $sw.Elapsed.TotalMilliseconds
        if ($resp -notmatch 'Flush ejecutado') {
            $status = 'ERROR'
            $note += 'flush: ' + $resp + '; '
        }
        else {
            $deadline = (Get-Date).AddSeconds([int]$Cfg.timeoutSec)
            $lastKa = Get-Date
            $errors = 0
            $status = 'TIMEOUT'
            while ((Get-Date) -lt $deadline) {
                try {
                    if ($Cfg.keepAlive -and ((Get-Date) - $lastKa).TotalSeconds -ge 10) {
                        Http-Post ($script:PoolBase + '/api/pool/miners/keepalive') '{"workerId":"gpu-sim-1"}' 10 | Out-Null
                        $lastKa = Get-Date
                    }
                    $idx = [int](Http-Get ($script:ApiBase + '/api/chain/stats') 10).latestBlockIndex
                    $errors = 0
                    if ($idx -gt $startIdx) { $status = 'OK'; break }
                }
                catch {
                    $errors++
                    if ($errors -ge 10) { $status = 'ERROR'; $note += 'sin respuesta de la API; '; break }
                }
                Start-Sleep -Milliseconds $PollMs
            }
            $totalMs = $sw.Elapsed.TotalMilliseconds
            if ($status -eq 'OK') {
                $blockIndex = $startIdx + 1
                # Los bloques enormes (str + bcContent) pesan decenas de MB: no se bajan.
                if ($txCount -le 1000) {
                    $info = Get-BlockInfo $blockIndex $txCount
                    if ($info.Note -ne '') { $note += $info.Note + '; ' }
                }
            }
        }
    }
    catch {
        $status = 'ERROR'
        $note += $_.Exception.Message + '; '
    }

    # 4. aislar la proxima corrida: vaciar la cola y dejar que terminen
    #    los chunks que quedaron en curso en los workers.
    #
    # Con muchos chunks (fragmentacion alta, ej. 1% = 100 chunks), el
    # chunk ganador confirma el bloque en segundos, pero los DEMAS
    # chunks de ese bloque siguen buscando -nada los cancela- y un
    # purge_queue + cooldown corto no alcanza a esperarlos. Sin este
    # chequeo, la proxima repeticion arranca con esos perdedores
    # todavia compitiendo por los workers, y termina en timeout aunque
    # su propia configuracion sea perfectamente resoluble (visto en la
    # corrida real: fragment=1% rep 1 OK en 4.2s, rep 2 TIMEOUT en
    # 300s con la misma config).
    if ($status -eq 'TIMEOUT' -or [int]$chunks -ge $HardResetChunkThreshold) {
        Invoke-HardReset
    }
    else {
        Reset-Queue
        $elapsedSec = 0
        if ($totalMs -ne '') { $elapsedSec = [double]$totalMs / 1000.0 }
        $cool = [Math]::Min(30.0, [Math]::Max([double]$MinCooldownSec, $elapsedSec * 1.5))
        if ($cool -gt 0) { Start-Sleep -Milliseconds ([int]($cool * 1000)) }
    }

    $prefixReq = ''
    if ($Cfg.prefix) { $prefixReq = ([string]$Cfg.prefix).Length }
    $row = [ordered]@{
        timestamp        = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss', $Inv)
        label            = $Label
        experiment       = $Cfg.experiment
        phase            = $Cfg.phase
        config           = $Cfg.config
        rep              = $Cfg.rep
        workers          = $workers
        tx_count         = $txCount
        prefix_len_req   = $prefixReq
        prefix_len_eff   = $info.PrefixLen
        chunks           = $chunks
        range            = $range
        fragment_pct     = $Cfg.fragmentPct
        ingest_ms        = (Num $ingest.Ms)
        ingest_server_ms = (Num $ingest.ServerMs)
        flush_ms         = (Num $flushMs)
        total_ms         = (Num $totalMs)
        status           = $status
        block_index      = $blockIndex
        nonce            = $info.Nonce
        winner           = $info.Winner
        tx_in_block      = $info.TxInBlock
        note             = $note.Trim()
    }
    [pscustomobject]$row | Export-Csv -Path $script:CsvPath -Append -NoTypeInformation -Encoding UTF8

    $shown = 'sin dato'
    if ($totalMs -ne '') { $shown = (Num ([Math]::Round([double]$totalMs, 0))) + ' ms' }
    Write-Log ('  ' + $Cfg.config + ' rep ' + $Cfg.rep + ': ' + $status + ' ' + $shown + ' (workers=' + $workers + ', chunks=' + $chunks + ')' + $(if ($info.Winner) { ' ganador=' + $info.Winner } else { '' }))
    return $status
}

# ---------------------------------------------------------------- escenarios
# Orden de busqueda: la corrida, luego "defaults", luego el nivel superior
# del escenario (donde estan repetitions y timeoutSec), luego el valor por defecto.
function Get-Cfg($Sc, $Run, [string]$Name, $Default) {
    $v = Get-Prop $Run $Name $null
    if ($null -ne $v) { return $v }
    $v = Get-Prop (Get-Prop $Sc 'defaults' $null) $Name $null
    if ($null -ne $v) { return $v }
    $v = Get-Prop $Sc $Name $null
    if ($null -ne $v) { return $v }
    return $Default
}

function Run-Scenario([string]$Name) {
    $path = Join-Path $ScenarioDir ($Name + '.json')
    $sc = Get-Content -Raw -Path $path | ConvertFrom-Json
    Write-Log ('=== Escenario ' + $Name + ' ===')

    if ((Get-Prop $sc 'type' '') -eq 'gpu') {
        $blockNo = 0
        foreach ($ph in $sc.phases) {
            Write-Log ('Fase ' + $ph.name + ' (gpu=' + $ph.gpu + ')')
            $wait = [int](Get-Prop $ph 'waitForExpirySec' 0)
            if ($wait -gt 0) { Write-Log ('  espero ' + $wait + ' s a que expire el keep-alive'); Start-Sleep -Seconds $wait }
            for ($b = 1; $b -le [int]$ph.blocks; $b++) {
                $blockNo++
                $cfg = @{
                    experiment = 'gpu'; phase = $ph.name; config = ('gpu=' + $(if ($ph.gpu) { 'on' } else { 'off' }))
                    rep = $blockNo; txCount = [int](Get-Cfg $sc $ph 'txCount' 10); prefix = ''
                    chunks = (Get-Cfg $sc $ph 'chunks' 'auto'); range = [long](Get-Cfg $sc $ph 'range' 200000000)
                    fragmentPct = ''; ingest = 'single'; timeoutSec = [int](Get-Cfg $sc $ph 'timeoutSec' 300)
                    keepAlive = [bool]$ph.gpu
                }
                Invoke-OneRun $cfg | Out-Null
            }
        }
        return
    }

    foreach ($run in $sc.runs) {
        $optional = [bool](Get-Prop $run 'optional' $false)
        $desc = ''
        $pct = Get-Prop $run 'fragmentPct' $null
        $prefix = [string](Get-Cfg $sc $run 'prefix' '')
        $txCount = [int](Get-Cfg $sc $run 'txCount' 10)
        if ($null -ne $pct) { $desc = 'fragment=' + $pct + '%' }
        elseif ($Name -eq 'bulk_transactions') { $desc = 'tx=' + $txCount }
        else { $desc = 'prefix=' + $prefix.Length }

        if ($optional -and -not $IncludeOptional) {
            Write-Log ('  (omito ' + $desc + ': opcional, usar -IncludeOptional)')
            continue
        }
        if ($Repetitions -gt 0) { $reps = $Repetitions } else { $reps = [int](Get-Cfg $sc $run 'repetitions' 3) }
        $chunks = Get-Cfg $sc $run 'chunks' 'auto'
        if ($null -ne $pct) { $chunks = [int][Math]::Round(100.0 / [double]$pct) }
        $ingest = [string](Get-Cfg $sc $run 'ingest' 'auto')
        if ($ingest -eq 'auto') { if ($txCount -le 100) { $ingest = 'single' } else { $ingest = 'generate' } }

        for ($r = 1; $r -le $reps; $r++) {
            $cfg = @{
                experiment = $(if ($Name -eq 'prefix_difficulty') { 'prefix' } elseif ($Name -eq 'pool_fragmentation') { 'fragmentation' } else { 'bulk' })
                phase = ''; config = $desc; rep = $r; txCount = $txCount; prefix = $prefix
                chunks = $chunks; range = [long](Get-Cfg $sc $run 'range' 10000000)
                fragmentPct = $(if ($null -ne $pct) { $pct } else { '' })
                ingest = $ingest; timeoutSec = [int](Get-Cfg $sc $run 'timeoutSec' 300); keepAlive = $false
            }
            Invoke-OneRun $cfg | Out-Null
        }
    }
}

# --------------------------------------------------------------------- main
$expNames = @{ prefix = 'prefix_difficulty'; fragmentation = 'pool_fragmentation'; bulk = 'bulk_transactions'; gpu = 'gpu_simulation' }
if ($Experiment -eq 'all') { $exps = @('prefix', 'fragmentation', 'bulk', 'gpu') } else { $exps = @($Experiment) }

if ($OutDir -eq '') {
    $OutDir = Join-Path (Join-Path $PSScriptRoot 'results') ((Get-Date).ToString('yyyyMMdd-HHmmss', $Inv) + '-' + $Label)
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$script:CsvPath = Join-Path $OutDir 'results.csv'
Write-Log ('Resultados en: ' + $script:CsvPath)

$externalMode = ($PoolUrl -ne '')
$envKeys = @('BLOCK_INTERVAL', 'POOL_TEST_ENDPOINTS')
try {
    if ($externalMode) {
        if ($ApiUrl -eq '') { throw 'Con -PoolUrl hay que indicar tambien -ApiUrl.' }
        $script:PoolBase = $PoolUrl.TrimEnd('/')
        $script:ApiBase = $ApiUrl.TrimEnd('/')
        if ($CoordUrl -ne '') { $script:CoordBase = $CoordUrl.TrimEnd('/') }
    }
    else {
        if (-not $NoClusterConfig) {
            $vars = @{ BLOCK_INTERVAL = '3600'; POOL_TEST_ENDPOINTS = 'true' }
            if ($exps -contains 'gpu') {
                $gsc = Get-Content -Raw -Path (Join-Path $ScenarioDir 'gpu_simulation.json') | ConvertFrom-Json
                $vars['POOL_PREFIX'] = [string]$gsc.poolPrefix
                $envKeys += 'POOL_PREFIX'
            }
            Set-PoolConfig $vars
        }
        # los tuneles se abren DESPUES del rollout: un port-forward al Service
        # queda atado a un pod y se cae si ese pod se reemplaza
        Start-Tunnel 'transaction-pool' $PoolPort 8082
        Start-Tunnel 'blockchain-api' $ApiPort 8080
        Start-Tunnel 'coordinator' $CoordPort 8081
        $script:PoolBase = 'http://127.0.0.1:' + $PoolPort
        $script:ApiBase = 'http://127.0.0.1:' + $ApiPort
        $script:CoordBase = 'http://127.0.0.1:' + $CoordPort
    }

    if (-not (Wait-Url ($script:PoolBase + '/api/pool/status'))) { throw 'El transaction-pool no responde en ' + $script:PoolBase }
    if (-not (Wait-Url ($script:ApiBase + '/api/chain/stats'))) { throw 'blockchain-api no responde en ' + $script:ApiBase }
    Test-Target
    Test-Capabilities

    $ctx = ($(Invoke-Kubectl @('config', 'current-context')) | Select-Object -First 1)
    $meta = [ordered]@{
        label = $Label; started = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss', $Inv)
        experiments = ($exps -join ','); include_optional = [bool]$IncludeOptional
        workers_at_start = (Get-WorkerCount); kubectl_context = [string]$ctx
        pool_url = $script:PoolBase; api_url = $script:ApiBase
    }
    ($meta | ConvertTo-Json) | Set-Content -Path (Join-Path $OutDir 'run_meta.json') -Encoding ASCII
    Write-Log ('Workers conectados a mining.tasks: ' + $meta.workers_at_start)

    foreach ($e in $exps) { Run-Scenario $expNames[$e] }

    Write-Log '=== Resumen (tiempo total medio por configuracion) ==='
    $rows = Import-Csv -Path $script:CsvPath
    $rows | Group-Object experiment, config | ForEach-Object {
        $ok = @($_.Group | Where-Object { $_.status -eq 'OK' -and $_.total_ms -ne '' })
        $bad = @($_.Group).Count - $ok.Count
        $avg = 'n/a'
        if ($ok.Count -gt 0) {
            $sum = 0.0
            foreach ($o in $ok) { $sum += [double]::Parse($o.total_ms, $Inv) }
            $avg = (Num ($sum / $ok.Count)) + ' ms'
        }
        Write-Log ('  ' + $_.Name + ' -> ' + $avg + ' (ok=' + $ok.Count + ', fallidas=' + $bad + ')')
    }
    Write-Log ('Listo. Graficos: python plot_results.py "' + $OutDir + '"')
}
finally {
    Stop-Tunnels
    if ($script:EnvChanged) { Restore-PoolConfig $envKeys }
}
