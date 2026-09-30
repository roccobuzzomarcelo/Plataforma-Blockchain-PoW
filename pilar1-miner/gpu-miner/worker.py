#!/usr/bin/env python3
"""
Worker GPU para la Plataforma Blockchain PoW.

Puente entre RabbitMQ (mining.tasks / mining.results, las mismas colas que
usan los workers Java) y el binario CUDA compilado a partir de
gpu-miner/src/main.cu. No reemplaza a los workers Java -corre AL LADO,
compite por los mismos chunks, exactamente como una VM externa más-.

Modos (variable de entorno GPU_WORKER_MODE):
  cuda (default) - llama al binario CUDA compilado (requiere GPU real)
  cpu            - fuerza bruta en Python puro, mismo protocolo exacto.
                    Sirve para probar TODO el circuito -RabbitMQ, formato
                    del mensaje, que el coordinator confirme el bloque-
                    sin necesitar la GPU todavia. Es mas lento, pero el
                    codigo que habla con RabbitMQ es EL MISMO en los dos
                    modos: el dia de la GPU real, cambiar de modo es
                    literalmente una variable de entorno.

Variables de entorno:
  RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER, RABBITMQ_PASS  (obligatorias)
  RABBITMQ_VHOST        default "/"
  WORKER_ID             default "gpu-worker-<hostname>"
  GPU_WORKER_MODE       "cuda" | "cpu"   default "cuda"
  GPU_BINARY_PATH       default "./gpu_worker_miner" (modo cuda)
  GPU_WORKER_GPU_SIM    "true"/"false"  default "true"
                         manda keep-alive de GPU (ver GpuKeepalive mas
                         abajo) para que el pool suba la dificultad.

Requiere: pip install pika
"""
import hashlib
import json
import os
import socket
import subprocess
import threading
import time

import pika

# ---------------------------------------------------------------- config
RABBITMQ_HOST = os.environ["RABBITMQ_HOST"]
RABBITMQ_PORT = int(os.environ.get("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.environ["RABBITMQ_USER"]
RABBITMQ_PASS = os.environ["RABBITMQ_PASS"]
RABBITMQ_VHOST = os.environ.get("RABBITMQ_VHOST", "/")

WORKER_ID = os.environ.get("WORKER_ID", f"gpu-worker-{socket.gethostname()}")
MODE = os.environ.get("GPU_WORKER_MODE", "cuda")
GPU_BINARY_PATH = os.environ.get("GPU_BINARY_PATH", "./gpu_worker_miner")
SEND_GPU_KEEPALIVE = os.environ.get("GPU_WORKER_GPU_SIM", "true").lower() == "true"
# Endpoint del keep-alive: solo alcanzable DENTRO del cluster/VPC del
# proyecto (nginx lo bloquea desde afuera a proposito). Si este worker
# corre en un cluster ajeno (el del profesor), probablemente no llegue
# -no es grave, la dificultad simplemente no sube; se puede seguir
# simulando el GPU con el loop de curl que ya usamos, desde una maquina
# que si tenga acceso interno.
KEEPALIVE_URL = os.environ.get(
    "KEEPALIVE_URL", "http://transaction-pool.blockchain-apps.svc.cluster.local:8082/api/pool/miners/keepalive"
)

TASKS_QUEUE = "mining.tasks"
RESULTS_EXCHANGE = "mining.results.exchange"
RESULTS_QUEUE = "mining.results"
# El binding del coordinator usa el NOMBRE DE LA COLA como routing key
# (asi lo hace tambien el ResultPublisher.java del worker real: es un
# DirectExchange, no fanout). Un routing key distinto -aunque sea
# parecido- hace que el mensaje se publique "bien" (sin error) pero
# nunca llegue a la cola: se pierde en silencio.
RESULTS_ROUTING_KEY = RESULTS_QUEUE
RESULT_TYPE_ID = "com.blockchain.shared.event.MiningResultEvent"


# ------------------------------------------------------------- mineria
def mine_cpu(str_field: str, bc_field: str, prefix: str, range_min: int, range_max: int):
    """Fuerza bruta en Python puro. Solo para probar el circuito sin GPU."""
    t0 = time.time()
    for nonce in range(range_min, range_max + 1):
        candidate = f"{nonce}{str_field}{bc_field}"
        digest = hashlib.md5(candidate.encode()).hexdigest()
        if digest.startswith(prefix):
            return nonce, digest, time.time() - t0
    return None, None, time.time() - t0


def mine_cuda(str_field: str, bc_field: str, prefix: str, range_min: int, range_max: int):
    """Invoca el binario CUDA compilado. Ver gpu-miner/src/main.cu."""
    t0 = time.time()
    proc = subprocess.run(
        [GPU_BINARY_PATH, str_field, bc_field, prefix, str(range_min), str(range_max)],
        capture_output=True, text=True, timeout=600,
    )
    elapsed = time.time() - t0
    if proc.returncode != 0:
        raise RuntimeError(f"gpu_worker_miner salio con error: {proc.stderr.strip()}")

    line = proc.stdout.strip().splitlines()[-1] if proc.stdout.strip() else ""
    if line.startswith("FOUND"):
        _, nonce_str, digest, _secs = line.split()
        return int(nonce_str), digest, elapsed
    if line.startswith("NOTFOUND"):
        return None, None, elapsed
    raise RuntimeError(f"Salida inesperada del binario CUDA: {proc.stdout!r} / stderr={proc.stderr!r}")


def mine(str_field: str, bc_field: str, prefix: str, range_min: int, range_max: int):
    if MODE == "cpu":
        return mine_cpu(str_field, bc_field, prefix, range_min, range_max)
    return mine_cuda(str_field, bc_field, prefix, range_min, range_max)


# --------------------------------------------------------------- keepalive
def gpu_keepalive_loop():
    import urllib.request
    body = json.dumps({"workerId": WORKER_ID}).encode()
    while True:
        try:
            req = urllib.request.Request(KEEPALIVE_URL, data=body, headers={"Content-Type": "application/json"})
            urllib.request.urlopen(req, timeout=5).read()
        except Exception as e:
            print(f"[{WORKER_ID}] keep-alive fallo (puede ser normal si este host no tiene red interna): {e}")
        time.sleep(10)  # el TTL del lado del pool es 30s (MinerMonitorService)


# ------------------------------------------------------------------- amqp
def on_task(channel, method, properties, body):
    try:
        task = json.loads(body)
    except json.JSONDecodeError:
        print(f"[{WORKER_ID}] mensaje no-JSON en {TASKS_QUEUE}, se descarta")
        channel.basic_ack(delivery_tag=method.delivery_tag)
        return

    task_id = task["taskId"]
    block_index = task["blockIndex"]
    str_field = task["str"]
    bc_field = task["bcContent"]
    prefix = task["prefix"]
    range_min = int(task["rangeMin"])
    range_max = int(task["rangeMax"])

    print(f"[{WORKER_ID}] tarea {task_id} (bloque {block_index}) prefix={prefix} "
          f"rango=[{range_min},{range_max}] modo={MODE}")

    try:
        nonce, digest, elapsed = mine(str_field, bc_field, prefix, range_min, range_max)
    except Exception as e:
        print(f"[{WORKER_ID}] ERROR minando tarea {task_id}: {e}")
        # No hacemos basic_ack: el mensaje vuelve a la cola para que otro
        # worker lo reintente (mismo comportamiento AMQP que un worker
        # Java que muere a mitad de una tarea -documentado en el
        # hallazgo de RabbitMQ del progreso del proyecto).
        return

    success = nonce is not None
    result = {
        "taskId": task_id,
        "workerId": WORKER_ID,
        "blockIndex": block_index,
        "nonce": nonce if success else 0,
        "blockHash": digest if success else "",
        "str": str_field,
        "bcContent": bc_field,
        "success": success,
        "elapsedMs": int(elapsed * 1000),
    }

    channel.basic_publish(
        exchange=RESULTS_EXCHANGE,
        routing_key=RESULTS_ROUTING_KEY,
        properties=pika.BasicProperties(
            content_type="application/json",
            headers={"__TypeId__": RESULT_TYPE_ID},
        ),
        body=json.dumps(result),
    )

    if success:
        print(f"[{WORKER_ID}] RESUELTO tarea {task_id}: nonce={nonce} hash={digest} ({elapsed:.3f}s)")
    else:
        print(f"[{WORKER_ID}] sin solucion en el rango, tarea {task_id} ({elapsed:.3f}s)")

    channel.basic_ack(delivery_tag=method.delivery_tag)


def main():
    print(f"[{WORKER_ID}] arrancando, modo={MODE}, rabbitmq={RABBITMQ_HOST}:{RABBITMQ_PORT}{RABBITMQ_VHOST}")
    if MODE == "cuda" and not os.path.isfile(GPU_BINARY_PATH):
        raise SystemExit(
            f"GPU_WORKER_MODE=cuda pero no existe {GPU_BINARY_PATH}. "
            f"Compilar primero: nvcc --cudart shared src/main.cu -o gpu_worker_miner"
        )

    if SEND_GPU_KEEPALIVE:
        threading.Thread(target=gpu_keepalive_loop, daemon=True).start()

    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    params = pika.ConnectionParameters(
        host=RABBITMQ_HOST, port=RABBITMQ_PORT, virtual_host=RABBITMQ_VHOST,
        credentials=credentials, heartbeat=30,
    )

    while True:
        try:
            connection = pika.BlockingConnection(params)
            channel = connection.channel()
            # La cola ya la declaran el coordinator y los workers Java
            # (durable=True, sin exclusive/autoDelete) -se re-declara
            # igual, es idempotente, por si este worker arranca antes
            # que cualquier otro componente.
            channel.queue_declare(queue=TASKS_QUEUE, durable=True)
            channel.basic_qos(prefetch_count=1)  # reparto parejo entre workers, igual que el lado Java
            channel.basic_consume(queue=TASKS_QUEUE, on_message_callback=on_task)
            print(f"[{WORKER_ID}] conectado, esperando tareas en {TASKS_QUEUE}...")
            channel.start_consuming()
        except pika.exceptions.AMQPConnectionError as e:
            print(f"[{WORKER_ID}] conexion perdida ({e}), reintento en 5s...")
            time.sleep(5)


if __name__ == "__main__":
    main()
