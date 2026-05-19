# Hit #5 - Hash por fuerza bruta con CUDA

## Objetivo
Encontrar un número (nonce) tal que al concatenarlo con una cadena
dada, el MD5 resultante comience con un prefijo específico.
Este es el núcleo del algoritmo Proof of Work (PoW).

## Concepto
```
MD5(cadena + nonce) = "00..."
↑
prefijo buscado
```

Como no existe forma de predecir qué nonce genera el prefijo buscado, la GPU prueba millones de combinaciones en paralelo. Cada hilo prueba un nonce distinto simultáneamente:

```
Hilo 0 → MD5("blockchain" + 0) = "a3f9..." ✗
Hilo 1 → MD5("blockchain" + 1) = "0012..." ✗
Hilo 2 → MD5("blockchain" + 2) = "00ab..." ✗
Hilo 3 → MD5("blockchain" + 3) = "0000..." ✓ ENCONTRADO
```

## Implementación

### Estrategia
- 256 bloques × 256 hilos = 65.536 nonces probados por batch
- Cada hilo calcula su nonce como: `start_nonce + blockIdx.x * blockDim.x + threadIdx.x`
- Se itera en batches hasta encontrar la solución
- `atomicCAS` garantiza que solo un hilo escriba el resultado

### Limitación del simulador
GPGPU-Sim no soporta multiplicaciones de 64 bits (`uint64_t`) dentro
de kernels. Se usó `uint32_t` para el nonce, suficiente para cubrir
hasta ~4.000 millones de combinaciones en el simulador.

Ver archivo: `brute_force.cu`

## Compilación y ejecución

```bash
nvcc --cudart shared brute_force.cu -o brute_force
./brute_force <cadena> <prefijo>
```

## Resultados obtenidos

| Cadena       | Prefijo | Nonce | MD5 resultante                     | Verificado |
| ------------ | ------- | ----- | ---------------------------------- | ---------- |
| `blockchain` | `0`     | 32    | `0b5c435631bad7b949b6c282c7951724` | ✓          |
| `blockchain` | `00`    | 226   | `007da4839a13aa248281a4f31925cf26` | ✓          |
| `blockchain` | `000`   | 2521  | `0005f6c1620b8b9dca39d7323904cb39` | ✓          |

### Verificación
Todos los resultados fueron verificados con `md5sum`:
```bash
echo -n "blockchain32"   | md5sum  # 0b5c435631bad7b949b6c282c7951724
echo -n "blockchain226"  | md5sum  # 007da4839a13aa248281a4f31925cf26
echo -n "blockchain2521" | md5sum  # 0005f6c1620b8b9dca39d7323904cb39
```

## Observaciones
- A mayor longitud del prefijo, mayor es el nonce encontrado,
  lo que sugiere que se necesitan más intentos para hallar la solución.
- Esta relación entre longitud del prefijo y dificultad de búsqueda
  se analiza en detalle en el Hit #6.

## Entorno
- Contenedor: srirajpaul/gpgpu-sim:0.2
- CUDA: 10.1
- Simulador: GPGPU-Sim 4.0.0
- Arquitectura simulada: sm_30
- Modo: functional simulation