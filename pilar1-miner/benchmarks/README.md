# Batería de Tests Comparativos GPU vs CPU

## Objetivo

Comparar el rendimiento del minero GPU (CUDA) contra el minero CPU
(Java 21 con ExecutorService) ejecutando los mismos casos de prueba
y analizando las diferencias en tiempo de ejecución.

## Entornos utilizados

### CPU

- Lenguaje: Java 21
- Paralelismo: ExecutorService con 8 hilos (núcleos disponibles)
- Sistema operativo: Windows
- Implementación: CpuMiner.java

### GPU

- Lenguaje: CUDA C++
- Plataforma: godbolt.org (hardware NVIDIA real, sm_75)
- Paralelismo: 256 bloques x 256 hilos = 65.536 nonces por batch
- Implementación: brute_force.cu (Hit #5)

## Casos de prueba

Los casos fueron definidos en `tests/test_cases.json` y cubren
tres cadenas distintas con prefijos de longitud 1 a 5 caracteres.

```json
{
  "test_cases": [
    { "id": 1,  "cadena": "blockchain", "prefijo": "0" },
    { "id": 2,  "cadena": "blockchain", "prefijo": "00" },
    { "id": 3,  "cadena": "blockchain", "prefijo": "000" },
    { "id": 4,  "cadena": "blockchain", "prefijo": "0000" },
    { "id": 5,  "cadena": "blockchain", "prefijo": "00000" },
    { "id": 6,  "cadena": "hello",      "prefijo": "0" },
    { "id": 7,  "cadena": "hello",      "prefijo": "00" },
    { "id": 8,  "cadena": "hello",      "prefijo": "000" },
    { "id": 9,  "cadena": "hello",      "prefijo": "0000" },
    { "id": 10, "cadena": "hello",      "prefijo": "00000" },
    { "id": 11, "cadena": "unlu2026",   "prefijo": "0" },
    { "id": 12, "cadena": "unlu2026",   "prefijo": "00" },
    { "id": 13, "cadena": "unlu2026",   "prefijo": "000" },
    { "id": 14, "cadena": "unlu2026",   "prefijo": "0000" },
    { "id": 15, "cadena": "unlu2026",   "prefijo": "00000" }
  ]
}
```

## Resultados

### CPU (Java 21 - 8 hilos)

| #  | Cadena       | Prefijo  | Nonce         | MD5 resultante                     | Tiempo    |
|----|--------------|----------|---------------|------------------------------------|-----------|
| 1  | `blockchain` | `0`      | 1.610.612.737 | `02b8746990e8608fb1cbb4d2fabfefbb` | 0.047 seg |
| 2  | `blockchain` | `00`     | 805.306.450   | `0006a517b9d90bf8985e45001c48d85c` | 0.079 seg |
| 3  | `blockchain` | `000`    | 268.435.526   | `0002235ff73ef0278691fb9e73dec8c2` | 0.075 seg |
| 4  | `blockchain` | `0000`   | 268.442.302   | `0000bbe045ca424b83e8dde175504f2a` | 0.834 seg |
| 5  | `blockchain` | `00000`  | 93.857        | `00000394fd2fb0aca0f57330cbb92c53` | 1.879 seg |
| 6  | `hello`      | `0`      | 1.879.048.193 | `033cbbea14cdc20544b43509753f8df0` | 0.045 seg |
| 7  | `hello`      | `00`     | 268.435.466   | `0085188fe0b1fb84150db9c2d7102132` | 0.054 seg |
| 8  | `hello`      | `000`    | 1.342.177.697 | `0009d4e83ef2a536f48942a678436f71` | 0.199 seg |
| 9  | `hello`      | `0000`   | 1.073.749.396 | `000013e4e4de77dbca1b977209144080` | 0.873 seg |
| 10 | `hello`      | `00000`  | 536.900.397   | `0000085c4128365c190c8223dc8d0c72` | 1.082 seg |
| 11 | `unlu2026`   | `0`      | 1.879.048.194 | `03bbb93084f2a9173620e1da83abc942` | 0.047 seg |
| 12 | `unlu2026`   | `00`     | 268.435.460   | `008312c5af54ff70ffcbdbd4dd91c199` | 0.051 seg |
| 13 | `unlu2026`   | `000`    | 805.307.186   | `000ba9b8656d3cdd2d97ce879891c39d` | 0.243 seg |
| 14 | `unlu2026`   | `0000`   | 268.441.415   | `0000e3f4dd4e6c451145b1295bae91eb` | 0.813 seg |
| 15 | `unlu2026`   | `00000`  | 1.073.829.712 | `00000e600a3b98e44f9099d7203ecdd3` | 1.806 seg |

### GPU (CUDA - godbolt.org sm_75)

| #  | Cadena       | Prefijo  | Nonce     | MD5 resultante                     | Tiempo      | Batches |
|----|--------------|----------|-----------|------------------------------------|-------------|---------|
| 1  | `blockchain` | `0`      | 8.967     | `013dc9d82b87cb03d149d8bbbe9a23ad` | 0.0004 seg  | 1       |
| 2  | `blockchain` | `00`     | 1.756     | `008895ef2581f7df54372e5633bbdd41` | 0.0004 seg  | 1       |
| 3  | `blockchain` | `000`    | 10.941    | `00009e1c0c8e664d78f60239eb73c96c` | 0.0004 seg  | 1       |
| 4  | `blockchain` | `0000`   | 10.941    | `00009e1c0c8e664d78f60239eb73c96c` | 0.0004 seg  | 1       |
| 5  | `blockchain` | `00000`  | 93.857    | `00000394fd2fb0aca0f57330cbb92c53` | 0.0005 seg  | 2       |
| 6  | `hello`      | `0`      | 8.101     | `021d3d3454d0da2f647b7ec12bb14680` | 0.0004 seg  | 1       |
| 7  | `hello`      | `00`     | 2.672     | `00e00970630f040404a53460507c0150` | 0.0004 seg  | 1       |
| 8  | `hello`      | `000`    | 17.846    | `0003560b142c7f8a33aa6cbd2d1113ae` | 0.0004 seg  | 1       |
| 9  | `hello`      | `0000`   | 105.484   | `0000049898d233686087e44bc2a1c97a` | 0.0005 seg  | 2       |
| 10 | `hello`      | `00000`  | 105.484   | `0000049898d233686087e44bc2a1c97a` | 0.0006 seg  | 2       |
| 11 | `unlu2026`   | `0`      | 2.712     | `09474b721b96dc08c6bbd334ab0d27ee` | 0.0004 seg  | 1       |
| 12 | `unlu2026`   | `00`     | 163       | `00b3f3eaf174e16b78226d448c7498fa` | 0.0004 seg  | 1       |
| 13 | `unlu2026`   | `000`    | 8.766     | `000d4c8f726bcdc16dfe27dd4093ad76` | 0.0004 seg  | 1       |
| 14 | `unlu2026`   | `0000`   | 73.176    | `0000d151d7d796effc00b128bdeaf2dc` | 0.0005 seg  | 2       |
| 15 | `unlu2026`   | `00000`  | 352.935   | `000007fd07cd1d00cb93faa9f13a0ae5` | 0.0009 seg  | 6       |

### Comparativa GPU vs CPU

| #  | Cadena       | Prefijo  | Tiempo CPU | Tiempo GPU | Factor GPU/CPU        |
|----|--------------|----------|------------|------------|-----------------------|
| 1  | `blockchain` | `0`      | 0.047 seg  | 0.0004 seg | **~118x más rápida**  |
| 2  | `blockchain` | `00`     | 0.079 seg  | 0.0004 seg | **~198x más rápida**  |
| 3  | `blockchain` | `000`    | 0.075 seg  | 0.0004 seg | **~188x más rápida**  |
| 4  | `blockchain` | `0000`   | 0.834 seg  | 0.0004 seg | **~2085x más rápida** |
| 5  | `blockchain` | `00000`  | 1.879 seg  | 0.0005 seg | **~3758x más rápida** |
| 6  | `hello`      | `0`      | 0.045 seg  | 0.0004 seg | **~113x más rápida**  |
| 7  | `hello`      | `00`     | 0.054 seg  | 0.0004 seg | **~135x más rápida**  |
| 8  | `hello`      | `000`    | 0.199 seg  | 0.0004 seg | **~498x más rápida**  |
| 9  | `hello`      | `0000`   | 0.873 seg  | 0.0005 seg | **~1746x más rápida** |
| 10 | `hello`      | `00000`  | 1.082 seg  | 0.0006 seg | **~1803x más rápida** |
| 11 | `unlu2026`   | `0`      | 0.047 seg  | 0.0004 seg | **~118x más rápida**  |
| 12 | `unlu2026`   | `00`     | 0.051 seg  | 0.0004 seg | **~128x más rápida**  |
| 13 | `unlu2026`   | `000`    | 0.243 seg  | 0.0004 seg | **~608x más rápida**  |
| 14 | `unlu2026`   | `0000`   | 0.813 seg  | 0.0005 seg | **~1626x más rápida** |
| 15 | `unlu2026`   | `00000`  | 1.806 seg  | 0.0009 seg | **~2007x más rápida** |

## Análisis

### Los nonces son diferentes entre CPU y GPU

Esto es comportamiento esperado. Ambos mineros buscan en paralelo
y el primero en encontrar cualquier nonce válido lo reporta. No
existe un único nonce correcto, hay infinitos nonces que cumplen
el prefijo buscado. Lo importante es que todos los hashes
encontrados son válidos y cumplen el prefijo requerido.

### La GPU es entre 100x y 3758x más rápida

La ventaja de la GPU crece con la longitud del prefijo. Para
prefijos cortos (1-2 caracteres) la solución aparece en el primer
batch tanto en GPU como en CPU, por lo que la diferencia es menor.
A medida que el prefijo crece, se necesitan más intentos y el
paralelismo masivo de la GPU (65.536 nonces por batch simultáneos)
marca una diferencia cada vez mayor.

### Los tiempos de GPU son casi constantes para prefijos cortos

Para prefijos de 1 a 3 caracteres el tiempo GPU ronda los 0.0004
segundos porque todo cabe en un único batch de 65.536 nonces. La
diferencia aparece a partir de prefijo 4-5 donde se necesitan
múltiples batches.

### Los tiempos de CPU son variables

Con 8 hilos dividiendo el espacio en chunks, el tiempo depende
de en qué chunk se encuentra la solución. Si la solución está al
inicio del chunk de un hilo, termina rápido. Si está al final,
tarda más. Esto explica la variabilidad observada entre casos con
diferente longitud de prefijo.

### Relevancia para el proyecto

Esta comparativa justifica la decisión arquitectural del Pilar 2:
usar GPU para el minado PoW siempre que esté disponible, y caer
en mineros CPU solo cuando no haya GPUs en la red. El Nodo
Coordinador (NCT) ajustará la dificultad (longitud del prefijo)
dinámicamente según los recursos disponibles.

## Conclusión

La GPU supera ampliamente a la CPU en tareas de minería PoW,
especialmente a medida que aumenta la dificultad. Para prefijos
largos (5+ caracteres), la diferencia supera las 2000x, lo que
hace imprescindible el uso de GPU en redes blockchain reales donde
la dificultad puede requerir prefijos de decenas de ceros.
