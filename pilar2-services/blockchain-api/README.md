# Blockchain API - Backend REST + WebSocket

## Descripción
Servicio backend que expone la blockchain al frontend via REST y notifica
cambios en tiempo real via WebSocket (STOMP). Lee el estado de la blockchain
desde Redis y actúa como punto de entrada para nuevas transacciones.

## Puerto: 8080

## Endpoints REST

### Blockchain
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/api/chain/blocks` | Todos los bloques confirmados |
| GET | `/api/chain/blocks/{index}` | Bloque por índice |
| GET | `/api/chain/blocks/latest` | Último bloque confirmado |
| GET | `/api/chain/transactions/pending` | Transacciones pendientes |
| GET | `/api/chain/stats` | Estadísticas generales |

### Transacciones
| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/transactions` | Enviar nueva transacción |

#### Body POST /api/transactions
```json
{
  "sender": "Alice",
  "receiver": "Bob",
  "amount": 10.5
}
```

## WebSocket
Endpoint: `ws://localhost:8080/ws` (con fallback SockJS)

### Tópicos disponibles
| Tópico | Descripción |
|---|---|
| `/topic/blocks` | Notificación cuando se mina un bloque nuevo |
| `/topic/workers` | Estado de los workers en tiempo real |

## Dependencias principales
- Spring Boot Web (REST)
- Spring Boot WebSocket (STOMP)
- Spring Data Redis
- Módulo `shared`

## Configuración
```properties
server.port=8080
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=redis123
services.transaction-pool.url=http://localhost:8082
```

## Levantar
```bash
mvn clean package -DskipTests
java -jar target/blockchain-api-1.0.0-SNAPSHOT.jar
```