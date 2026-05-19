# Hit #3 - Librerías CUDA

## ¿Qué es CCCL?
CUDA C++ Core Libraries (nvidia/cccl) es un conjunto de librerías
de C++ para programación en GPU que agrupa tres componentes:
- **Thrust**: algoritmos paralelos de alto nivel (sort, reduce, etc.)
- **CUB**: primitivas de bajo nivel optimizadas para GPU
- **libcu++**: implementación de la STL de C++ para GPU

## ¿Qué es Thrust?
Thrust es la librería de alto nivel dentro de CCCL que permite
escribir código paralelo para GPU de forma similar a la STL de
C++, sin necesidad de escribir kernels manualmente.

## ¿Thrust necesita instalación adicional?
No. Thrust ya viene incluido con CUDA. Verificado con:
```bash
ls /usr/local/cuda/include/thrust/
```
El comando lista todos los headers disponibles, confirmando que
Thrust está preinstalado junto con CUDA 10.1 en el contenedor.

## Código ejecutado

### thrust_host_only.cu (GPGPU-Sim)
Demuestra `thrust::sort` y `thrust::reduce` sobre `host_vector`
sin necesidad de GPU real.
Ver archivo: `thrust_host_only.cu`

### thrust_vectors.cu (godbolt.org)
Demuestra transferencia CPU → GPU → CPU con `device_vector`
sobre hardware NVIDIA real.
Ver archivo: `thrust_vectors.cu`

## Resultados

### Ejemplo host (GPGPU-Sim)
`thrust::sort` y `thrust::reduce` funcionaron correctamente sobre
`host_vector` sin necesidad de GPU real.

```powershell
Vector original:
h_vec[0] = 50
h_vec[1] = 10
h_vec[2] = 40
h_vec[3] = 20
h_vec[4] = 30
Vector ordenado con thrust::sort:
h_vec[0] = 10
h_vec[1] = 20
h_vec[2] = 30
h_vec[3] = 40
h_vec[4] = 50
Suma total con thrust::reduce: 150
```

### Ejemplo device_vector (godbolt.org, sm_75, hardware NVIDIA real)
La transferencia CPU → GPU → CPU con `device_vector` funcionó
correctamente en hardware NVIDIA real.

```powershell
Vector en CPU:
h_vec[0] = 10
h_vec[1] = 20
h_vec[2] = 30
h_vec[3] = 40
h_vec[4] = 50
Vector copiado a GPU exitosamente.
Vector recuperado de GPU:
h_result[0] = 10
h_result[1] = 20
h_result[2] = 30
h_result[3] = 40
h_result[4] = 50
Thrust device_vector funciona correctamente!
```

## Limitación encontrada
La transferencia con `thrust::device_vector` produce Segmentation
Fault en GPGPU-Sim 4.0.0 debido a soporte incompleto de las
operaciones de memoria de Thrust en el simulador.

El ejemplo fue verificado exitosamente en godbolt.org con
arquitectura sm_75 y hardware NVIDIA real, donde funcionó
correctamente.

Esta limitación no afecta el desarrollo del proyecto ya que
el minero PoW usará CUDA puro, donde el simulador sí funciona
correctamente (verificado en Hit #2).

## CUDA puro vs Thrust

| Aspecto            | CUDA puro                   | Thrust                        |
| ------------------ | --------------------------- | ----------------------------- |
| Abstracción        | Baja, control total         | Alta, declarativo             |
| Gestión de memoria | Manual (cudaMalloc/Free)    | Automática con device_vector  |
| Kernels            | Escritos por el programador | Ya implementados              |
| Curva aprendizaje  | Alta                        | Baja si sabés STL             |
| Flexibilidad       | Total                       | Limitada a algoritmos de CCCL |
| Rendimiento        | Máximo si bien escrito      | Muy bueno, optimizado NVIDIA  |
| Caso de uso ideal  | Algoritmos específicos      | Operaciones estándar          |

## Conclusión
Para el minero PoW del proyecto se usará CUDA puro ya que la
búsqueda del nonce por fuerza bruta requiere control total de
hilos y rangos. Thrust sería útil para ordenar o reducir
resultados pero no es necesario en este caso.