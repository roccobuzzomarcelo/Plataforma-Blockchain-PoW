# Shared - Modelos y Utilidades Comunes

## Descripción

Módulo Maven compartido entre todos los microservicios del Pilar 2.
Define los modelos del dominio, eventos y utilidades de hash.
Debe instalarse en el repositorio Maven local antes de compilar
cualquier otro servicio.

## Contenido

### Modelos (`model/`)

- **`Transaction`** — Transferencia entre usuarios (sender → receiver, amount).
  Soporta dos tipos: `TRANSFER` (normal) y `COINBASE` (recompensa al worker ganador).
- **`Block`** — Bloque de la blockchain con su hash, transacciones, nonce,
  prefijo de dificultad y referencia al bloque anterior. Estados: `CONFIRMED`.
- **`MiningTask`** — Tarea publicada por el Coordinator en RabbitMQ con toda
  la información necesaria para que un Worker intente resolver el PoW.
  Incluye el rango de búsqueda del nonce `[rangeMin, rangeMax]`.

### Eventos (`event/`)

- **`MiningResultEvent`** — Resultado enviado por un Worker al Coordinator
  cuando encuentra (o no) un nonce válido en su rango asignado.
  Incluye `success`, `nonce`, `blockHash` y `elapsedMs`.
- **`BlockMinedEvent`** — Evento broadcast del Coordinator a blockchain-api
  cuando un bloque es confirmado. Contiene `blockIndex`, `blockHash`,
  `winnerWorkerId`, `nonce`, `prefix` y `reward`.

### Utilidades (`util/`)

- **`HashUtils`** — Cálculo de MD5 y SHA-256. El hash PoW se calcula como:
  `MD5(nonce + str + bcContent)`.

## Algoritmo de Hash PoW

```bash
hash = MD5(nonce + str + bcContent)
donde:
nonce     = número encontrado por el worker (long)
str       = índice del bloque + campos de cada transacción concatenados
bcContent = previousHash + campos de cada transacción concatenados
El hash es válido si comienza con el prefijo de dificultad (ej: "000").
```

## Estructura de un Bloque

```java
Block {
  index        → posición en la cadena (0 = génesis)
  previousHash → hash del bloque anterior (garantiza integridad)
  transactions → lista de transacciones (TRANSFER + COINBASE)
  nonce        → número que resuelve el PoW
  blockHash    → MD5(nonce + str + bcContent)
  str          → contenido del bloque sin hash previo
  bcContent    → contenido del bloque con hash previo
  prefix       → dificultad del PoW (ej: "000")
  timestamp    → fecha/hora de confirmación
  status       → CONFIRMED
}
```

## Instalación

```bash
cd pilar2-services/shared
mvn clean install
```

El artefacto queda disponible en `~/.m2/repository/com/blockchain/shared/`.
Debe reinstalarse cada vez que se modifique este módulo.

## Dependencia en otros servicios

```xml
<dependency>
    <groupId>com.blockchain</groupId>
    <artifactId>shared</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```
