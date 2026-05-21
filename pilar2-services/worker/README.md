# Worker - Nodo Minero CPU

## Descripción
Nodo trabajador que se suscribe al tópico de RabbitMQ, recibe tareas
de minería y compite con otros workers por resolver el Proof of Work.
Usa Java 21 con ExecutorService para paralelizar la búsqueda del nonce.
El primero en encontrar una solución válida la envía al Coordinator.

## Puerto: 8083

## Funcionamiento

### 1. Suscripción
Al arrancar se conecta a RabbitMQ y se suscribe al tópico de tareas.
Cada instancia genera un ID único para identificarse ante el Coordinator.

### 2. Recepción de tarea
Al recibir una MiningTask del Coordinator extrae:
- `str` y `bcContent` para construir el input del hash
- `prefix` como objetivo del PoW
- `rangeMin` y `rangeMax` como límites de búsqueda

### 3. Minería
Busca un nonce en el rango asignado tal que:
`MD5(nonce + str + bcContent)` comience con el prefijo.
Usa múltiples hilos (uno por núcleo disponible) para maximizar
la velocidad de búsqueda.

### 4. Notificación del resultado
Si encuentra el nonce: POST al Coordinator con el resultado.
Si no encuentra en el rango: notifica que no encontró solución.
Si otro worker ya resolvió la tarea: descarta y espera nueva tarea.

## Configuración
```properties
server.port=8083
worker.threads=8
services.coordinator.url=http://localhost:8081
```

## Levantar múltiples instancias
```bash
# Instancia 1
java -jar target/worker-1.0.0-SNAPSHOT.jar --server.port=8083

# Instancia 2
java -jar target/worker-1.0.0-SNAPSHOT.jar --server.port=8084

# Instancia 3
java -jar target/worker-1.0.0-SNAPSHOT.jar --server.port=8085
```

## Levantar
```bash
mvn clean package -DskipTests
java -jar target/worker-1.0.0-SNAPSHOT.jar
```