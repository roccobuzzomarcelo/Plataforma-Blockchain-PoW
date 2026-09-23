# Smoke test end-to-end — `smoke-test.ps1`

Script de PowerShell que verifica en un minuto que el stack completo del Pilar 2 funciona de punta a punta: servicios levantados, validación de transacciones, minado distribuido entre workers, integridad criptográfica de la cadena y persistencia de Redis ante una caída abrupta.

Se usa como **prueba de regresión**: después de cualquier cambio en el código o en la configuración, se corre el script y, si da `0 FAIL`, el sistema sigue funcionando como antes.

---

## Requisitos

- Windows PowerShell 5.1 o PowerShell 7.
- Docker Desktop con Docker Compose v2 (`docker compose`).
- Puertos libres: `80`, `5672`, `6379`, `8080`–`8084` y `15672`.
- El script está escrito solo con caracteres ASCII para que PowerShell 5.1 lo lea bien con cualquier codificación (ver [Solución de problemas](#solución-de-problemas)).

## Ubicación y ejecución

El script vive en `pilar2-services/scripts/` y **se ejecuta siempre desde `pilar2-services/`**, porque usa `docker compose` y `docker build` con rutas relativas a esa carpeta.

```powershell
cd pilar2-services
Set-ExecutionPolicy -Scope Process Bypass      # solo para la sesión actual
Unblock-File .\scripts\smoke-test.ps1          # si el archivo fue descargado

.\scripts\smoke-test.ps1                       # testea el stack que ya está levantado
.\scripts\smoke-test.ps1 -Build                # reconstruye imágenes y levanta el stack
.\scripts\smoke-test.ps1 -Build -Clean         # idem, borrando volúmenes (Redis y RabbitMQ vacíos)
.\scripts\smoke-test.ps1 -Persistence          # agrega la prueba de caída de Redis
.\scripts\smoke-test.ps1 -Rounds 10            # más rondas para ver la distribución de ganadores
```

## Parámetros

| Parámetro         | Tipo   | Default | Descripción                                                                                                                                                                                     |
|-------------------|--------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `-Build`          | switch | —       | Ejecuta `docker compose down`, construye las 5 imágenes (`blockchain-api`, `coordinator`, `transaction-pool`, `worker`, `blockchain-frontend`) y levanta el stack con `docker compose up -d`.                                                         |
| `-Clean`          | switch | —       | Solo tiene efecto junto con `-Build`: usa `docker compose down -v` para borrar los volúmenes y arrancar con la cadena vacía.                                                                    |
| `-Persistence`    | switch | —       | Agrega el paso 6: mata Redis con SIGKILL y verifica que la cadena sobreviva.                                                                                                                    |
| `-TxPerRound`     | int    | `5`     | Transacciones enviadas en cada ronda de minado.                                                                                                                                                 |
| `-Rounds`         | int    | `2`     | Cantidad de rondas de minado (un bloque por ronda).                                                                                                                                             |
| `-MineTimeoutSec` | int    | `180`   | Tiempo máximo de espera para que aparezca cada bloque nuevo.                                                                                                                                    |

> **Importante:** si se modifica `docker-compose.yml` sin cambiar código, no hace falta `-Build`, pero el stack sí tiene que recrearse para tomar el cambio: `docker compose down -v; docker compose up -d` y después el script sin `-Build`.

---

## Qué verifica cada paso

### 1. Salud de los servicios

Espera hasta 120 s a que respondan los endpoints de estado y luego verifica el resto:

| Servicio          | Verificación                                   |
| ----------------- | ---------------------------------------------- |
| blockchain-api    | `GET :8080/api/chain/stats`                    |
| coordinator       | `GET :8081/api/coordinator/status`             |
| transaction-pool  | `GET :8082/api/pool/status`                    |
| worker-1/worker-2 | `GET :8083` y `:8084` `/api/worker/status`     |
| frontend          | `GET http://localhost/` (nginx sirve el SPA)   |
| RabbitMQ          | `GET :15672/api/queues` (API de management)    |

De RabbitMQ lista las colas con su cantidad de consumidores. La salida esperada es `mining.results` más dos colas anónimas `spring.gen-*` (una por worker), cada una con `1c`: confirma el esquema híbrido de colas y tópicos de P2.

Si blockchain-api, coordinator o transaction-pool no responden, el script corta acá y sugiere revisar `docker compose logs`.

### 2. Bloque génesis e integridad inicial

Comprueba que exista una cadena (`latestBlockIndex >= 0`) y corre la verificación de integridad completa (ver [Verificación de integridad](#verificación-de-integridad)).

### 3. Validaciones de entrada

Envía a `POST /api/transactions` de blockchain-api tres transacciones inválidas y espera `400 Bad Request` en cada una:

- monto `0`;
- `sender` igual a `receiver`;
- `sender` vacío.

### 4. Rondas de minado

Por cada ronda:

1. Envía `-TxPerRound` transacciones válidas por `POST /api/transactions` (la ruta con validación). Los emisores y receptores se marcan como `smoke<HHmmss>-r<ronda>-A<n>` / `-B<n>` para identificarlas después.
2. Verifica que el pool tenga al menos esa cantidad de pendientes (`GET /api/pool/status`).
3. Fuerza el procesamiento con `POST /api/pool/flush`, sin esperar el scheduler de 60 s.
4. Consulta `GET /api/chain/stats` cada 500 ms hasta que aparezca un bloque nuevo.
5. Descarga el bloque nuevo y comprueba que contenga **todas** las transacciones de la ronda y **exactamente una** transacción `COINBASE`, cuyo receptor es el worker ganador.

Muestra el `nonce`, el `blockHash` y el `prefix` usados.

### 5. Estado posterior

- Vuelve a verificar la integridad de toda la cadena.
- Confirma que ninguna transacción de la corrida quedó pendiente en el pool.
- Confirma que ninguna transacción (excepto las coinbase) aparece en dos bloques distintos.
- Muestra cuántos bloques ganó cada worker.

### 6. Persistencia de Redis (`-Persistence`)

Simula una caída real: `docker compose kill redis` envía **SIGKILL**, de modo que Redis no tiene la oportunidad de guardar su snapshot RDB al apagarse. Luego lo vuelve a iniciar, espera a que blockchain-api responda y compara la cantidad de bloques antes y después, además de verificar la integridad.

> Un `docker compose restart` **no** sirve para esta prueba: apaga Redis de forma ordenada y Redis guarda el RDB durante el apagado, así que la prueba pasaría aunque no hubiera persistencia real.

---

## Verificación de integridad

Para cada bloque de `GET /api/chain/blocks`, ordenado por índice, el script recalcula en PowerShell lo mismo que `HashUtils.powHash()` del módulo `shared`:

```java
blockHash == md5(nonce + str + bcContent)
```

y comprueba además:

| Regla                                       | Qué detecta                                     |
| ------------------------------------------- | ----------------------------------------------- |
| índices contiguos desde 0                   | bloques faltantes o duplicados                  |
| `status == CONFIRMED`                       | bloques a medio confirmar                       |
| hash recalculado == `blockHash`             | alteración del contenido o del nonce            |
| `bcContent` empieza con `previousHash`      | contenido desvinculado del bloque anterior      |
| `previousHash` == `blockHash` del anterior  | ruptura del encadenamiento                      |
| `blockHash` empieza con `prefix`            | bloque sin Proof of Work válido                 |

Al génesis (índice 0) no se le exige el prefijo, porque `GenesisService` lo crea con `nonce = 0` sin minado real.

---

## Salida de ejemplo

```powershell
== 4.1 Ronda de minado 1/2 (5 transacciones)
  [OK]   Enviadas 5 transacciones via blockchain-api
  [OK]   Pool tiene 5 pendientes
  flush -> Flush ejecutado: 5 transacciones enviadas al coordinator
  [OK]   Bloque nuevo minado en 0,6 s
  [OK]   El bloque 1 contiene nuestras 5 transacciones (6 en total)
  [OK]   Tiene exactamente 1 coinbase (ganador: worker-1, recompensa: 50.0)
  nonce=7500004  hash=0015dcab0f3f7a2f10aceab2f04472ab  prefix=00
...
== 6. Persistencia de Redis (caida abrupta: docker kill = SIGKILL)
  [OK]   La cadena sobrevivio a la caida (3 -> 3 bloques)
  [OK]   Cadena integra: 3 bloques verificados (hash, encadenamiento, prefijo)

Resultado: 28 OK, 0 FAIL
```

El script termina con código de salida `1` si hubo algún `FAIL`, lo que permite usarlo más adelante en un pipeline de CI.

---

## Resultados obtenidos: persistencia de Redis

La prueba del paso 6 se corrió con y sin AOF (Append Only File) en Redis:

| Configuración de Redis                            | Resultado tras SIGKILL                            |
| ------------------------------------------------- | --------------------------------------------------|
| `redis-server --requirepass ...` (solo RDB)       | **3 → 0 bloques** (se perdió incluso el génesis)  |
| `redis-server --requirepass ... --appendonly yes` | **3 → 3 bloques**, cadena íntegra                 |

Sin AOF, Redis solo escribe a disco con snapshots RDB periódicos; en una corrida corta ningún snapshot llega a hacerse y una caída pierde todo. Con AOF, cada escritura se registra en el log y se sincroniza a disco cada segundo (`appendfsync everysec`, valor por defecto), así que la pérdida máxima ante una caída es del último segundo de escrituras. Por eso `docker-compose.yml` usa `--appendonly yes`, y es la configuración que se lleva al StatefulSet de Kubernetes.

Para confirmar que el AOF está activo:

```powershell
docker exec blockchain-redis redis-cli -a redis123 CONFIG GET appendonly   # debe responder "yes"
```

---

## Observaciones que surgen del test

- **El prefijo usado es `00` y no `000`.** Es el comportamiento pedido en P5: sin mineros GPU enviando keep-alive, `MinerMonitorService` informa que no hay capacidad GPU y `SplitService.adjustDifficulty()` reduce la dificultad. La variable `MINING_PREFIX` del compose no afecta al transaction-pool, porque `pool.default-prefix` está fijo en `000` en su `application.properties`.
- **Nonces cercanos al inicio de un rango** (por ejemplo `7500004` o `2500044`): cada worker divide su rango entre varios hilos (`WORKER_THREADS=8`) que buscan en paralelo, así que el primer hallazgo suele aparecer pocos pasos después del inicio de algún sub-rango.
- **Tiempo de minado:** con el prefijo `00` el minado es casi instantáneo. Los "0,6 s" que se reportan reflejan sobre todo el intervalo de consulta de 500 ms, no el tiempo real de minado. Para mediciones de rendimiento (Pilar 3, sección 3.3) hay que usar los tiempos que registran los propios servicios.
- **Caída de Redis sin AOF:** si Redis pierde los datos con el coordinador en marcha, la cadena queda vacía y el génesis no se recrea hasta reiniciar el coordinador, porque `GenesisService.initializeIfNeeded()` solo corre al inicio.

---

## Solución de problemas

| Síntoma | Causa | Solución |
| ------- | ----- | -------- |
| `'.\scripts\smoke-test.ps1' no se reconoce como nombre de un cmdlet...` | El archivo no está en esa ruta o la terminal no está en `pilar2-services` | `Get-Location` y `Test-Path .\scripts\smoke-test.ps1` |
| `Falta la llave de cierre "}"` o texto como `sobreviviÃ³` / `â†’` | El archivo tiene caracteres UTF-8 y PowerShell 5.1 lo leyó como Windows-1252 (el byte `0x92` se interpreta como comilla) | Usar la versión ASCII del script, o volver a guardarlo como *UTF-8 with BOM* |
| "No se puede cargar el archivo... la ejecución de scripts está deshabilitada" | Política de ejecución de Windows | `Set-ExecutionPolicy -Scope Process Bypass` y `Unblock-File` |
| El paso 6 da `3 -> 0` | AOF desactivado | Verificar `--appendonly yes` en `docker-compose.yml` con `docker compose config \| Select-String appendonly` y recrear el stack |
| Un cambio en `docker-compose.yml` no se refleja | El stack no se recreó (`-Clean` sin `-Build` no hace nada) | `docker compose down -v; docker compose up -d` |
| Falla la verificación de RabbitMQ | Credenciales distintas de `admin:admin123` | Ajustar la línea de `$cred` en el script |

## Limitaciones

- Las URLs y las credenciales de RabbitMQ están fijas en el script (`localhost` y `admin:admin123`, los valores por defecto del compose). Para usarlo contra minikube o GKE habrá que parametrizarlas.
- Solo verifica un worker por puerto (`8083` y `8084`); si se agregan más workers al compose, hay que sumarlos a `$WORKERS`.
- La coinbase se agrega al bloque después del minado y no forma parte de `str` ni de `bcContent`, por lo que la verificación de integridad no puede detectar una alteración de la recompensa.
