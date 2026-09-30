# Minero GPU externo — puente RabbitMQ ↔ CUDA

Worker que corre en un clúster ajeno al proyecto (el que te va a prestar el
profesor) y compite por chunks de minería igual que un pod o la VM externa
— la única diferencia es dónde vive el proceso.

## Los dos archivos

| Archivo | Qué es |
| --- | --- |
| `src/main.cu` | El kernel CUDA. Deriva de `hits/hit7-range-limits/range_miner.cu`, con dos correcciones: usa el orden real `nonce + str + bcContent` (no `cadena + nonce`), y nonces de 64 bits en vez de 32 (el Hit #7 se queda corto para rangos grandes). **Validado sin GPU**: el algoritmo MD5 multi-bloque se comparó contra `hashlib.md5` en 20 casos (incluidos los bordes de 55 a 4096 bytes), y el orden de concatenación se verificó contra el bloque génesis real de tu clúster. |
| `worker.py` | El puente a RabbitMQ. Consume de `mining.tasks`, le pasa el trabajo al binario CUDA (o a una fuerza bruta en Python puro, modo `cpu`), y publica el resultado en `mining.results` con el formato exacto que espera el coordinator. |

## Por qué conviene probar HOY, sin la GPU

`worker.py` tiene dos modos (`GPU_WORKER_MODE=cpu` o `cuda`). El código que
habla con RabbitMQ —conectar, declarar la cola, publicar el resultado con
el header `__TypeId__`— es **exactamente el mismo** en los dos modos. Solo
cambia qué función calcula el hash. Si probás hoy en modo `cpu`, contra tu
propio clúster o Docker Compose, validás todo lo que puede fallar por
mensajería o formato — y el día de la GPU real, pasar a modo `cuda` es
cambiar una sola variable de entorno, no escribir código nuevo bajo presión
de tiempo.

### Prueba de hoy (modo CPU, sin GPU)

```powershell
cd pilar1-miner\gpu-miner
pip install pika

# Contra el cluster real (con un port-forward, como siempre)
kubectl port-forward -n blockchain-infra svc/rabbitmq 15673:5672

$env:RABBITMQ_HOST="localhost"
$env:RABBITMQ_PORT="15673"
$env:RABBITMQ_USER="admin"
$env:RABBITMQ_PASS="admin123"
$env:GPU_WORKER_MODE="cpu"
$env:GPU_WORKER_GPU_SIM="false"   # sin acceso interno desde afuera, el keep-alive no va a llegar igual
$env:WORKER_ID="gpu-worker-test-cpu"

python worker.py
```

Con eso corriendo, mandá una transacción y forzá el minado desde el
frontend (otro `port-forward`, como siempre). Si `worker.py` imprime
`RESUELTO tarea ... nonce=... hash=...` y el bloque aparece confirmado en
el frontend con este `WORKER_ID` como ganador en algunos bloques, el
circuito completo — RabbitMQ, formato del mensaje, consenso del
coordinator — está validado de punta a punta. Lo único que falta ese día
es la GPU real.

### El día de la GPU (modo CUDA)

En el clúster del profesor:

```bash
# Compilar (requiere nvcc + una GPU NVIDIA visible)
cd gpu-miner
nvcc --cudart shared src/main.cu -o gpu_worker_miner

# Probar el binario solo, sin RabbitMQ, para confirmar que compila y corre
./gpu_worker_miner "test" "test" "0" 0 1000

pip install pika
export RABBITMQ_HOST=<ver "Red" abajo>
export RABBITMQ_PORT=5672
export RABBITMQ_USER=<usuario restringido, ver mas abajo>
export RABBITMQ_PASS=<password del usuario restringido>
export GPU_WORKER_MODE=cuda
export GPU_BINARY_PATH=./gpu_worker_miner
export WORKER_ID=gpu-worker-real

python worker.py
```

## Red: cómo llega el clúster del profesor a nuestro RabbitMQ

Nuestro RabbitMQ hoy solo es alcanzable dentro de la VPC del proyecto
(`10.0.0.100`, el Internal Load Balancer que usa la VM minera). Un clúster
ajeno no puede llegar ahí. Hace falta exponerlo temporalmente — ver
`expose-rabbitmq-temporal.yaml` y las instrucciones en ese archivo. **Se
aplica recién cuando coordines la ventana de prueba con el profesor, y se
revierte apenas termines** — no dejarlo expuesto de forma permanente.

## Límites conocidos

- El buffer del kernel (`MAX_INPUT`, 8192 bytes) alcanza para bloques de
  varias docenas de transacciones. Para el escenario de carga con miles
  de transacciones (bulk test de la 3.3) hay que agrandarlo y recompilar
  — no está pensado para ese caso tal cual.
- No se pudo probar el binario CUDA en sí en ningún momento de esta
  preparación — no hay GPU NVIDIA disponible acá. Lo que sí se validó,
  sin necesidad de GPU, fue el algoritmo (contra `hashlib`) y el puente a
  RabbitMQ (modo `cpu`, contra el cluster real). El único paso que queda
  genuinamente pendiente para el día de la GPU es confirmar que compila y
  corre en el hardware real.
