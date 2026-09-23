<#
.SYNOPSIS
  Smoke test end-to-end de la Plataforma Blockchain PoW (stack docker-compose).

.DESCRIPTION
  Ubicacion sugerida: pilar2-services/scripts/smoke-test.ps1
  Ejecutar desde pilar2-services/:
      .\scripts\smoke-test.ps1                    # solo testea (stack ya levantado)
      .\scripts\smoke-test.ps1 -Build             # reconstruye imagenes y levanta el stack
      .\scripts\smoke-test.ps1 -Build -Clean      # idem, pero borrando volumenes (Redis/RabbitMQ vacios)
      .\scripts\smoke-test.ps1 -Rounds 3 -Persistence

  Compatible con Windows PowerShell 5.1 y PowerShell 7.
#>
param(
    [switch]$Build,
    [switch]$Clean,
    [switch]$Persistence,
    [int]$TxPerRound = 5,
    [int]$Rounds = 2,
    [int]$MineTimeoutSec = 180
)

$ErrorActionPreference = 'Stop'
$API    = 'http://localhost:8080'
$COORD  = 'http://localhost:8081'
$POOL   = 'http://localhost:8082'
$WORKERS = @('http://localhost:8083', 'http://localhost:8084')
$RUN_ID = Get-Date -Format 'HHmmss'

$script:passed = 0; $script:failed = 0
function Ok($msg)   { Write-Host "  [OK]   $msg" -ForegroundColor Green; $script:passed++ }
function Fail($msg) { Write-Host "  [FAIL] $msg" -ForegroundColor Red;   $script:failed++ }
function Step($msg) { Write-Host "`n== $msg" -ForegroundColor Cyan }
function Check($cond, $msg) { if ($cond) { Ok $msg } else { Fail $msg } }

function Md5Hex([string]$s) {
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $bytes = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($s))
    -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function Get-Json($url) { Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 10 }

function Post-Json($url, $body) {
    $json = if ($null -ne $body) { $body | ConvertTo-Json -Compress } else { '' }
    Invoke-RestMethod -Uri $url -Method Post -Body $json -ContentType 'application/json' -TimeoutSec 15
}

function Get-StatusCode($url, $body) {
    try {
        $json = $body | ConvertTo-Json -Compress
        $r = Invoke-WebRequest -Uri $url -Method Post -Body $json -ContentType 'application/json' -UseBasicParsing -TimeoutSec 10
        return [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

function Wait-Url($url, $name, $timeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try { Get-Json $url | Out-Null; Ok "$name responde"; return $true } catch { Start-Sleep -Seconds 3 }
    }
    Fail "$name no respondio en $timeoutSec s ($url)"; return $false
}

function Test-StompWebSocket($uri) {
    # Abre un WebSocket real (exige que el proxy reenvie Upgrade/Connection),
    # manda un frame STOMP CONNECT y devuelve la primera respuesta del broker.
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $ct = [Threading.CancellationToken]::None
    try {
        if (-not $ws.ConnectAsync([Uri]$uri, $ct).Wait(5000)) { return 'timeout al conectar' }
        $frame = "CONNECT`naccept-version:1.2`nhost:localhost`n`n" + [char]0
        $out = [System.ArraySegment[byte]]::new([Text.Encoding]::UTF8.GetBytes($frame))
        $ws.SendAsync($out, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait(5000) | Out-Null
        $buf = New-Object byte[] 2048
        $task = $ws.ReceiveAsync([System.ArraySegment[byte]]::new($buf), $ct)
        if (-not $task.Wait(5000)) { return 'sin respuesta STOMP' }
        return [Text.Encoding]::UTF8.GetString($buf, 0, $task.Result.Count)
    } catch {
        $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }
        return "error: $($e.Message)"
    } finally { $ws.Dispose() }
}

function Get-Chain {
    $blocks = Get-Json "$API/api/chain/blocks"   # variable intermedia: en PS 5.1 el array JSON llega como un solo objeto
    ,@($blocks | Sort-Object { [int]$_.index })
}

function Test-ChainIntegrity {
    $chain = Get-Chain
    $errors = @()
    for ($i = 0; $i -lt $chain.Count; $i++) {
        $b = $chain[$i]
        if ([int]$b.index -ne $i) { $errors += "indice esperado $i, encontrado $($b.index)" }
        if ($b.status -ne 'CONFIRMED') { $errors += "bloque $i con status $($b.status)" }
        $recalc = Md5Hex ("$($b.nonce)" + $b.str + $b.bcContent)
        if ($recalc -ne $b.blockHash) { $errors += "bloque ${i}: hash no coincide (recalculado $recalc)" }
        if (-not $b.bcContent.StartsWith($b.previousHash)) { $errors += "bloque ${i}: bcContent no arranca con previousHash" }
        if ($i -gt 0) {
            if ($b.previousHash -ne $chain[$i - 1].blockHash) { $errors += "bloque ${i}: previousHash no apunta al bloque $($i-1)" }
            if (-not $b.blockHash.StartsWith($b.prefix)) { $errors += "bloque ${i}: hash sin prefijo '$($b.prefix)'" }
        }
    }
    if ($errors.Count -eq 0) { Ok "Cadena integra: $($chain.Count) bloques verificados (hash, encadenamiento, prefijo)" }
    else { $errors | ForEach-Object { Fail $_ } }
    return $chain
}

# -------------------------------------------------------------
if ($Build) {
    Step 'Build de imagenes'
    if ($Clean) { docker compose down -v } else { docker compose down }
    $images = @(
        @{ df = 'blockchain-api/Dockerfile';   tag = 'blockchain-api:latest' },
        @{ df = 'coordinador/Dockerfile';      tag = 'coordinator:latest' },
        @{ df = 'transaction-pool/Dockerfile'; tag = 'transaction-pool:latest' },
        @{ df = 'worker/Dockerfile';           tag = 'worker:latest' },
        @{ df = 'frontend/Dockerfile';         tag = 'blockchain-frontend:latest' }
    )
    foreach ($img in $images) {
        Write-Host "  docker build $($img.tag)"
        docker build -q -f $img.df -t $img.tag . | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Fallo el build de $($img.tag)" }
    }
    Step 'docker compose up'
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw 'Fallo docker compose up' }
}

Step '1. Salud de los servicios'
$up = (Wait-Url "$API/api/chain/stats" 'blockchain-api') -and
      (Wait-Url "$COORD/api/coordinator/status" 'coordinator') -and
      (Wait-Url "$POOL/api/pool/status" 'transaction-pool')
$WORKERS | ForEach-Object { Wait-Url "$_/api/worker/status" "worker ($_)" 60 | Out-Null }
try { Invoke-WebRequest 'http://localhost/' -UseBasicParsing -TimeoutSec 10 | Out-Null; Ok 'frontend (nginx) sirve index.html' } catch { Fail 'frontend no responde en http://localhost/' }
try {
    $cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:admin123'))
    $q = Invoke-RestMethod 'http://localhost:15672/api/queues' -Headers @{ Authorization = "Basic $cred" } -TimeoutSec 10
    Ok "RabbitMQ management OK ($(@($q).Count) colas: $((@($q) | ForEach-Object { "$($_.vhost)$($_.name)[$($_.consumers)c]" }) -join ', '))"
} catch { Fail "RabbitMQ management API no responde: $($_.Exception.Message)" }
if (-not $up) { Write-Host "`nServicios caidos, reviso logs con: docker compose logs --tail 50" -ForegroundColor Yellow; exit 1 }

Step '1b. Proxy reverso de nginx (frontend en :80)'
$WEB = 'http://localhost'
try { Get-Json "$WEB/api/chain/stats" | Out-Null; Ok '/api/chain/* -> blockchain-api' } catch { Fail "/api/chain/stats via nginx: $($_.Exception.Message)" }
try { Get-Json "$WEB/api/pool/status" | Out-Null; Ok '/api/pool/* -> transaction-pool' } catch { Fail "/api/pool/status via nginx: $($_.Exception.Message)" }
Check ((Get-StatusCode "$WEB/api/transactions" @{ sender = 'A'; receiver = 'A'; amount = 10 }) -eq 400) '/api/transactions -> blockchain-api (validacion devuelve 400)'
Check ((Get-StatusCode "$WEB/api/events/block-mined" @{}) -eq 404) '/api/events/* bloqueado (endpoint interno del coordinator)'
Check ((Get-StatusCode "$WEB/api/pool/miners/keepalive" @{}) -eq 404) '/api/pool/miners/* bloqueado (keep-alive interno)'
try {
    $info = Get-Json "$WEB/ws/info"
    Check ($info.websocket -eq $true) '/ws/info responde (endpoint SockJS de blockchain-api)'
} catch { Fail "/ws/info via nginx: $($_.Exception.Message)" }
$stomp = Test-StompWebSocket 'ws://localhost/ws/websocket'
Check ($stomp -like 'CONNECTED*') "WebSocket + STOMP a traves de nginx ($(($stomp -split "`n")[0]))"

Step '2. Bloque genesis e integridad inicial'
$stats = Get-Json "$API/api/chain/stats"
Check ([int]$stats.latestBlockIndex -ge 0) "Existe cadena (ultimo indice: $($stats.latestBlockIndex))"
Test-ChainIntegrity | Out-Null

Step '3. Validaciones de entrada (deben devolver 400)'
Check ((Get-StatusCode "$API/api/transactions" @{ sender = 'A'; receiver = 'B'; amount = 0 }) -eq 400) 'Monto 0 rechazado'
Check ((Get-StatusCode "$API/api/transactions" @{ sender = 'A'; receiver = 'A'; amount = 10 }) -eq 400) 'sender == receiver rechazado'
Check ((Get-StatusCode "$API/api/transactions" @{ sender = ''; receiver = 'B'; amount = 10 }) -eq 400) 'sender vacio rechazado'

$winners = @{}
for ($r = 1; $r -le $Rounds; $r++) {
    Step "4.$r Ronda de minado $r/$Rounds ($TxPerRound transacciones)"
    $before = [int](Get-Json "$API/api/chain/stats").latestBlockIndex
    $tag = "smoke$RUN_ID-r$r"
    for ($t = 1; $t -le $TxPerRound; $t++) {
        Post-Json "$API/api/transactions" @{ sender = "$tag-A$t"; receiver = "$tag-B$t"; amount = (10 * $t) } | Out-Null
    }
    Ok "Enviadas $TxPerRound transacciones via blockchain-api"
    $pending = [int](Get-Json "$POOL/api/pool/status").pendingTransactions
    Check ($pending -ge $TxPerRound) "Pool tiene $pending pendientes"

    $sw = [Diagnostics.Stopwatch]::StartNew()
    Write-Host '  flush ->' (Post-Json "$POOL/api/pool/flush" $null)
    $mined = $false
    while ($sw.Elapsed.TotalSeconds -lt $MineTimeoutSec) {
        if ([int](Get-Json "$API/api/chain/stats").latestBlockIndex -gt $before) { $mined = $true; break }
        Start-Sleep -Milliseconds 500
    }
    $sw.Stop()
    if (-not $mined) { Fail "No se mino un bloque nuevo en $MineTimeoutSec s"; continue }
    Ok ("Bloque nuevo minado en {0:N1} s" -f $sw.Elapsed.TotalSeconds)

    $block = Get-Json "$API/api/chain/blocks/$($before + 1)"
    $txs = @($block.transactions)
    $ours = @($txs | Where-Object { $_.sender -like "$tag-*" })
    $coinbase = @($txs | Where-Object { $_.type -eq 'COINBASE' })
    Check ($ours.Count -eq $TxPerRound) "El bloque $($block.index) contiene nuestras $TxPerRound transacciones ($($txs.Count) en total)"
    Check ($coinbase.Count -eq 1) "Tiene exactamente 1 coinbase (ganador: $($coinbase[0].receiver), recompensa: $($coinbase[0].amount))"
    Write-Host "  nonce=$($block.nonce)  hash=$($block.blockHash)  prefix=$($block.prefix)"
    if ($coinbase.Count -ge 1) { $winners[$coinbase[0].receiver] = 1 + [int]$winners[$coinbase[0].receiver] }
}

Step '5. Estado posterior'
$chain = Test-ChainIntegrity
$poolTxs = Get-Json "$POOL/api/pool/transactions"
$left = @($poolTxs | Where-Object { $_.sender -like "smoke$RUN_ID-*" })
Check ($left.Count -eq 0) 'No quedaron transacciones de esta corrida en el pool'
$dupes = @($chain | ForEach-Object { $_.transactions } | Where-Object { $_.type -ne 'COINBASE' } | Group-Object id | Where-Object Count -gt 1)
Check ($dupes.Count -eq 0) 'Ninguna transaccion aparece en dos bloques'
Write-Host "  Ganadores: $(($winners.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', ')"

if ($Persistence) {
    Step '6. Persistencia de Redis (caida abrupta: docker kill = SIGKILL)'
    $countBefore = @($chain).Count
    docker compose kill redis | Out-Null      # sin shutdown ordenado: Redis no llega a guardar el RDB
    docker compose start redis | Out-Null
    Start-Sleep -Seconds 8
    Wait-Url "$API/api/chain/stats" 'blockchain-api tras la caida de Redis' 60 | Out-Null
    $after = Get-Chain                        # variable intermedia: Get-Chain devuelve el array envuelto
    $countAfter = @($after).Count
    Check ($countAfter -eq $countBefore) "La cadena sobrevivio a la caida ($countBefore -> $countAfter bloques)"
    if ($countAfter -gt 0) { Test-ChainIntegrity | Out-Null }
}

Write-Host ("`nResultado: {0} OK, {1} FAIL" -f $script:passed, $script:failed) -ForegroundColor $(if ($script:failed) { 'Red' } else { 'Green' })
if ($script:failed) { exit 1 }
