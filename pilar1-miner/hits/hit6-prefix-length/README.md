# Hit #6 - Longitudes de prefijo en CUDA HASH

## Objetivo
Analizar la relación entre la longitud del prefijo buscado y el
tiempo requerido para encontrar el nonce correspondiente, usando
el programa de fuerza bruta del Hit #5.

## Metodología
Se ejecutó el programa `prefix_test.cu` con la cadena "blockchain"
y prefijos de longitud creciente (1 a 6 caracteres), midiendo:
- Nonce encontrado
- Tiempo de ejecución
- Cantidad de batches necesarios

Las pruebas se realizaron en dos entornos:
- **GPGPU-Sim**: simulador dentro del contenedor Docker
- **godbolt.org** (sm_75): hardware NVIDIA real, para prefijos
  donde el simulador resultó inviable

Ver archivo: `prefix_test.cu`

## Compilación y ejecución

```bash
nvcc --cudart shared prefix_test.cu -o prefix_test
./prefix_test <cadena> <prefijo>
```

## Resultados obtenidos

| Longitud | Prefijo    | Nonce      | MD5 resultante                     | Tiempo simulador | Tiempo godbolt | Batches |
| -------- | ---------- | ---------- | ---------------------------------- | ---------------- | -------------- | ------- |
| 1        | `0`        | 32         | `0b5c435631bad7b949b6c282c7951724` | 0.628 seg        | —              | 1       |
| 2        | `00`       | 226        | `007da4839a13aa248281a4f31925cf26` | 0.527 seg        | —              | 1       |
| 3        | `000`      | 2.521      | `0005f6c1620b8b9dca39d7323904cb39` | 1.630 seg        | —              | 1       |
| 4        | `0000`     | 10.941     | `00009e1c0c8e664d78f60239eb73c96c` | 6.037 seg        | —              | 1       |
| 5        | `00000`    | 93.857     | `00000394fd2fb0aca0f57330cbb92c53` | 44.497 seg       | —              | 2       |
| 6        | `000000`   | 18.000.230 | `00000039de763517a9799aada09a4370` | >10 min (cancel) | 0.037 seg      | 275     |

## Análisis

### Relación longitud de prefijo vs intentos promedio
Cada carácter del prefijo pertenece al alfabeto hexadecimal (16
símbolos posibles: 0-9 y a-f). Por lo tanto, la probabilidad de
que una posición coincida es 1/16. Para un prefijo de longitud N:

```
Intentos promedio = 16^N
Prefijo 1 → 16^1  =         16 intentos
Prefijo 2 → 16^2  =        256 intentos
Prefijo 3 → 16^3  =      4.096 intentos
Prefijo 4 → 16^4  =     65.536 intentos
Prefijo 5 → 16^5  =  1.048.576 intentos
Prefijo 6 → 16^6  = 16.777.216 intentos
```
Esto confirma que la dificultad crece de forma **exponencial**
con la longitud del prefijo, lo cual es exactamente el principio
detrás del algoritmo Proof of Work (PoW) en blockchain: aumentar
un carácter del prefijo multiplica por 16 el trabajo requerido.

### Comparativa simulador vs hardware real
El tiempo en GPGPU-Sim no refleja el rendimiento real de una GPU
ya que el simulador ejecuta cada instrucción secuencialmente para
emular el comportamiento paralelo. Esto se evidencia claramente
en el prefijo de longitud 6:

- **GPGPU-Sim**: >10 minutos (cancelado)
- **godbolt.org** (hardware NVIDIA real): 0.037 segundos

La diferencia es de varios órdenes de magnitud. En hardware real,
275 batches de 65.536 nonces cada uno se procesan en paralelo
en milisegundos.

### Prefijo más largo encontrado
El prefijo más largo encontrado fue de **6 caracteres** (`000000`),
con nonce 18.000.230, verificado en godbolt.org con hardware NVIDIA
real. En el simulador no fue posible completar la búsqueda en un
tiempo razonable.

## Conclusión
La longitud del prefijo es el principal parámetro de dificultad
del algoritmo PoW. En la blockchain real de Bitcoin, el prefijo
equivalente tiene decenas de ceros, lo que requiere hardware
especializado (ASICs) para resolverse en tiempos competitivos.

En nuestro proyecto, el Nodo Coordinador de Tareas (NCT) será el
responsable de ajustar dinámicamente la longitud del prefijo según
la capacidad de procesamiento disponible en la red de miners.

## Entorno
- Contenedor: srirajpaul/gpgpu-sim:0.2 (GPGPU-Sim 4.0.0, sm_30)
- godbolt.org: nvcc, sm_75, hardware NVIDIA real