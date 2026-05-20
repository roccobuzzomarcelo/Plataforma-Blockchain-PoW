# Shared - Modelos y Utilidades Comunes

## Descripción
Módulo Maven compartido entre todos los microservicios del Pilar 2.
Define los modelos del dominio, eventos y utilidades de hash.

## Contenido

### Modelos (`model/`)
- **`Transaction`** — Transferencia entre usuarios (sender → receiver, amount).
  Soporta dos tipos: `TRANSFER` (normal) y `COINBASE` (recompensa al worker ganador).
- **`Block`** — Bloque de la blockchain con su hash, transacciones, nonce,
  prefijo de dificultad y referencia al bloque anterior.
- **`MiningTask`** — Tarea publicada por el Coordinator en RabbitMQ con toda
  la información necesaria para que un Worker intente resolver el PoW.

### Eventos (`event/`)
- **`MiningResultEvent`** — Resultado enviado por un Worker al Coordinator
  cuando encuentra (o no) un nonce válido en su rango asignado.
- **`BlockMinedEvent`** — Evento broadcast del Coordinator a todos los clientes
  WebSocket cuando un bloque es confirmado exitosamente.

### Utilidades (`util/`)
- **`HashUtils`** — Funciones MD5, SHA-256 y PoW específicas del proyecto.
  El hash del PoW se calcula como: `MD5(nonce + str + bcContent)`.

## Algoritmo de Hash PoW
```bash
# hash(nonce + str + bcContent)
# donde:
# nonce     = número encontrado por el worker
# str       = índice del bloque + hash de cada transacción
# bcContent = previousHash + hash de cada transacción
```

## Instalación
```bash
mvn clean install
```
El artefacto queda disponible en `~/.m2/repository/com/blockchain/shared/`.

## Dependencia en otros servicios
```xml
<dependency>
    <groupId>com.blockchain</groupId>
    <artifactId>shared</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```