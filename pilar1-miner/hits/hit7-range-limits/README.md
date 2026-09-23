# Hit #7 - Hash por fuerza bruta con CUDA (con límites)

## Objetivo

Modificar el programa de fuerza bruta del Hit #5 para buscar el
nonce únicamente dentro de un rango numérico dado. Si no existe
solución en ese rango, el programa lo informa explícitamente.

## Concepto

En el contexto de una blockchain distribuida, limitar el rango de
búsqueda del nonce es fundamental para el Pool de Transacciones
(TrP): permite dividir el espacio de búsqueda entre múltiples
workers, donde cada uno es responsable de un rango distinto.

```bash
Worker 1 → busca nonce en [0,       65.535]
Worker 2 → busca nonce en [65.536, 131.071]
Worker 3 → busca nonce en [131.072, 196.607]
```

## Cambios respecto al Hit #5

- Se agregan dos parámetros nuevos: `nonce_min` y `nonce_max`
- El kernel verifica que `nonce > end_nonce` antes de procesar
- El loop de batches respeta el límite superior del rango
- Si no se encuentra solución se informa con un mensaje claro
- Se valida que `nonce_min <= nonce_max` antes de ejecutar

Ver archivo: `range_miner.cu`

## Compilación y ejecución

```bash
nvcc --cudart shared range_miner.cu -o range_miner
./range_miner <cadena> <prefijo> <nonce_min> <nonce_max>
```

## Casos de prueba

### Caso 1 — Rango que contiene la solución

```bash
./range_miner "blockchain" "00" 0 1000
```

### Caso 2 — Rango que NO contiene la solución

```bash
./range_miner "blockchain" "00" 0 100
```

### Caso 3 — Rango exacto en el límite

```bash
./range_miner "blockchain" "00" 226 226
```

### Caso 4 — Rango inválido (min > max)

```bash
./range_miner "blockchain" "00" 1000 100
```

## Resultados

| Caso | Rango | Resultado | Tiempo |
| ---- | ----- | --------- | ------ |
| Rango con solución | [0, 1000] | Nonce 226 encontrado ✓ | 0.632 seg |
| Rango sin solución | [0, 100] | No encontrado | 0.437 seg |
| Rango exacto | [226, 226] | Nonce 226 encontrado ✓ | 0.426 seg |
| Rango inválido | [1000, 100] | ERROR informado | — |

## Relevancia para el proyecto

Este programa es la base del Worker en el Pilar 2. El Pool de
Transacciones (TrP) fragmentará el espacio de búsqueda del nonce
en rangos y los distribuirá entre los workers via RabbitMQ. Cada
worker ejecutará este algoritmo sobre su rango asignado y notificará
al Nodo Coordinador (NCT) si encuentra una solución.

## Entorno

- Contenedor: srirajpaul/gpgpu-sim:0.2
- CUDA: 10.1
- Simulador: GPGPU-Sim 4.0.0
- Arquitectura simulada: sm_30
- Modo: functional simulation
