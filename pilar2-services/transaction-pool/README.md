# Transaction Pool (TrP) - Pool de Transacciones

## Descripción
Servicio que recibe transacciones individuales, las acumula en Redis
y cada 60 segundos las agrupa en un bloque para enviarlo al Coordinator.
También gestiona la disponibilidad de miners GPU y ajusta la dificultad
dinámicamente según los recursos disponibles.

## Puerto: 8082

## Responsabilidades

### 1. Recepción de transacciones
Recibe transacciones via REST desde el blockchain-api y las almacena
en Redis como pendientes.

### 2. Agrupación en bloques (cada 60 segundos)
Toma todas las transacciones pendientes, las agrupa en un bloque
y notifica al Coordinator para que inicie el proceso de minería.

### 3. Fragmentación de rangos
Subdivide el espacio de búsqueda del nonce en rangos y los distribuye
entre los workers disponibles para búsqueda paralela.

### 4. Gestión de miners GPU (keep-alive)
Recibe señales de vida de los miners GPU. Si no hay GPU disponibles:
- Reduce la dificultad del prefijo
- Levanta/destruye instancias de miners CPU dinámicamente

## Endpoints REST
| Método | Endpoint                     | Descripción                    |
| ------ | ---------------------------- | ------------------------------ |
| POST   | `/api/pool/transactions`     | Recibe nueva transacción       |
| GET    | `/api/pool/transactions`     | Lista transacciones pendientes |
| POST   | `/api/pool/miners/keepalive` | Keep-alive de miner GPU        |
| GET    | `/api/pool/status`           | Estado del pool                |

## Configuración
```properties
server.port=8082
pool.block-interval=60
pool.default-prefix=000
pool.mining-reward=50.0
```

## Levantar
```bash
mvn clean package -DskipTests
java -jar target/transaction-pool-1.0.0-SNAPSHOT.jar
```