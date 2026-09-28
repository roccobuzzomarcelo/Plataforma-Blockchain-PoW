# Pruebas de carga y escalabilidad (seccion 3.3)

Mide como se comporta la plataforma en GKE bajo distintas configuraciones y
genera los CSV y graficos para el informe.

| Archivo | Que es |
| --- | --- |
| `run_experiments.ps1` | Corre los escenarios contra el cluster y guarda `results.csv` |
| `plot_results.py` | Genera los graficos y `summary.md` a partir de uno o mas `results.csv` |
| `scenarios/*.json` | La matriz de pruebas de cada experimento |
| `results/` | Salida de cada corrida (una carpeta por ejecucion) |

## Que pide la guia y donde se cubre

| Requisito de 3.3 | Escenario |
| --- | --- |
| Prefijo de hash de 1 a 8 caracteres | `prefix_difficulty` (el 8 es opcional, ver abajo) |
| Fragmentacion del pool del 1% al 50% | `pool_fragmentation` |
| Bulks de 1 a 100.000 transacciones | `bulk_transactions` (el de 100.000 es opcional) |
| Ingreso y egreso de nodos GPU | `gpu_simulation` (keep-alive simulado) |
| Ingreso y egreso de nodos CPU | correr el mismo escenario con la VM externa apagada y prendida (`-Label`) |

## Antes de correr

1. **Reconstruir y desplegar las imagenes `transaction-pool` y `coordinator`.**
   El runner necesita los parametros nuevos de `flush` y los endpoints de prueba.
   Si quedo una imagen vieja, el runner lo detecta al arrancar y aborta con un
   mensaje claro (sin eso, mediria configuraciones distintas de las pedidas sin
   ningun error).
2. `kubectl` apuntando al cluster (`kubectl get nodes` responde) y RabbitMQ y
   Redis sanos.
3. Ningun otro `kubectl port-forward` abierto en los puertos 18080-18082 (el
   script abre y cierra los suyos, en `127.0.0.1`).
4. Para los graficos: `pip install pandas matplotlib`.

## Correr

```powershell
cd pilar3-infra\test-load

# un experimento
.\run_experiments.ps1 -Experiment prefix
.\run_experiments.ps1 -Experiment fragmentation
.\run_experiments.ps1 -Experiment bulk
.\run_experiments.ps1 -Experiment gpu

# todos, con las corridas opcionales (lentas o riesgosas)
.\run_experiments.ps1 -Experiment all -IncludeOptional

# graficos (una o varias carpetas de resultados)
python plot_results.py results\<carpeta>
python plot_results.py results\*-vm_off results\*-vm_on --out results\comparacion
```

Comparar 2 workers contra 3 (VM minera externa): prendela con
`gcloud compute instance-groups managed resize external-miner-mig --zone=us-central1-a --project=sdypp-rocco --size=1`,
espera a que `mining.tasks` tenga 3 consumidores y corre el mismo experimento con
`-Label vm_on`; con la VM apagada, `-Label vm_off`. Los chunks se alinean solos
con la cantidad de workers conectados (`"chunks": "auto"`).

## Que hace el script (y restaura al terminar)

- Configura el `transaction-pool` del cluster con `BLOCK_INTERVAL=3600` (apaga el
  scheduler de 60 s, para que ningun ciclo automatico procese transacciones de una
  prueba) y `POOL_TEST_ENDPOINTS=true`. Al terminar, o con Ctrl+C, lo restaura.
  Mientras dura la prueba **no se mina solo** y `/api/pool/test/*` queda accesible
  (nginx expone `/api/pool/` al navegador).
- Si cerraste la ventana a la fuerza y quedo sin restaurar:
  `kubectl set env deployment/transaction-pool BLOCK_INTERVAL- POOL_TEST_ENDPOINTS- POOL_PREFIX- -n blockchain-apps`
- Verifica que el destino sea el cluster: la cantidad de bloques de la API debe
  coincidir con el `SCARD` del Redis del cluster.
- Entre corridas vacia la cola `mining.tasks` y espera a que terminen los chunks que
  quedaron en curso. Si una corrida no resuelve en el tiempo maximo (`TIMEOUT`),
  reinicia los workers del cluster para descartar el trabajo en curso (los de la VM
  externa **no** se reinician).

## Como leer los resultados

- **Tiempo** = desde el pedido de `flush` hasta que la API informa el bloque
  confirmado, sondeando cada 100 ms. Incluye el viaje por la red y la mensajeria,
  no solo el calculo.
- **Piso de latencia (~200-300 ms)**: con prefijos cortos el bloque se resuelve en
  pocos cientos de hashes, casi instantaneo. Lo que se mide ahi es la latencia de
  `transaction-pool` -> `coordinator` -> RabbitMQ -> worker -> `coordinator`, no la
  potencia de calculo. La diferencia entre workers solo aparece cuando el trabajo
  supera ese piso (prefijos de 5 o mas caracteres).
- **x16 por caracter**: cada caracter hexadecimal multiplica por 16 el trabajo
  esperado (16^n hashes). El grafico de prefijos incluye esa referencia.
- **CPU**: prefijos de 7 caracteres pueden tardar minutos; el de 8 (`optional`)
  puede tardar decenas de minutos o mas, con mucha varianza. Es el caso donde una
  GPU marca la diferencia.
- **Ganador**: con dificultad baja, gana el primer resultado que llega, que
  depende mas del orden de entrega de RabbitMQ y de la latencia que de la
  velocidad de calculo. No es un problema del consenso.

## Prueba de 100.000 transacciones (opcional)

Un bloque de 100.000 transacciones pesa decenas de MB (el bloque guarda `str` y
`bcContent` ademas de las transacciones) y con el heap por defecto (25% de 512 MiB)
puede agotar la memoria del coordinator, del pool o de los workers. Antes de
`-IncludeOptional` en `bulk`:

```powershell
foreach ($d in 'coordinator','transaction-pool','worker') {
  kubectl set env deployment/$d JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -n blockchain-apps
  kubectl set resources deployment/$d --limits=memory=1536Mi -n blockchain-apps
}
```

Despues: `foreach ($d in ...) { kubectl set env deployment/$d JAVA_TOOL_OPTIONS- -n blockchain-apps }`
y `kubectl apply -f ..\k8s\apps\` para volver a los limites del repo. Que falle
tambien es un resultado valido: queda registrado como `TIMEOUT`/`ERROR` y es
material para el analisis de escalabilidad (el pool hace un GET y un DELETE a
Redis por transaccion, O(n) viajes de red por bloque).

## Limitaciones conocidas (para el informe)

- Un chunk cuyo bloque ya se resolvio sigue minandose hasta terminar: no hay
  cancelacion. El script lo aisla vaciando la cola y esperando, pero es
  trabajo desperdiciado real del sistema.
- Si un bloque no se resuelve dentro del rango de nonces, sus transacciones se
  pierden (el pool ya las vacio). Por eso el rango crece con el prefijo.
- Los prefijos de 1 a 4 caracteres estan dominados por el piso de latencia.
- Las corridas `TIMEOUT` reinician los workers del cluster.
