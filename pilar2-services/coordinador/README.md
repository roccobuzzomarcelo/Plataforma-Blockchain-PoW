# Coordinator - Nodo Coordinador de Tareas (NCT)

## Descripción
Cerebro del sistema blockchain. Responsable de formar bloques con las
transacciones pendientes, publicar tareas de minería en RabbitMQ,
verificar los resultados de los workers, confirmar bloques válidos
y entregar recompensas al worker ganador.

## Puerto: 8081

## Responsabilidades

### 1. Inicialización
Al arrancar verifica si existe el bloque génesis en Redis.
Si no existe, lo crea automáticamente (index=0, previousHash=0...0).

### 2. Publicación de tareas (NCT.1)
Recibe bloques formados del Transaction Pool y los publica
como tópico en RabbitMQ con:
- Hash del último bloque confirmado
- Lista de transacciones pendientes
- Nivel de dificultad actual (prefijo)
- Rango de búsqueda del nonce

### 3. Competencia de workers (NCT.2)
Los workers compiten por resolver el PoW. El primero en encontrar
un nonce válido notifica al Coordinator via POST /api/coordinator/solved_task.

### 4. Verificación (NCT.3)
Al recibir un resultado, el Coordinator recalcula el hash localmente:
`MD5(nonce + str + bcContent)` y verifica que comience con el prefijo.

### 5. Confirmación y recompensa (NCT.4)
Si el hash es válido:
- Crea una transacción COINBASE (recompensa al worker ganador)
- Guarda el bloque completo en Redis
- Notifica a todos los clientes via WebSocket
- Descarta resultados tardíos de otros workers

## Endpoints REST
| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/coordinator/solved_task` | Recibe resultado de un worker |
| GET | `/api/coordinator/status` | Estado del coordinator |

## Configuración de minería
```properties
mining.reward=50.0
mining.prefix=000
mining.block-interval=60
```

## Levantar
```bash
mvn clean package -DskipTests
java -jar target/coordinator-1.0.0-SNAPSHOT.jar
```