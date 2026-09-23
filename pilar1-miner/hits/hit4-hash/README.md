# Hit #4 - Hash MD5 con CUDA

## Objetivo

Escribir un programa que reciba un string por parámetro y calcule
su MD5 utilizando la GPU, devolviendo el hash calculado por consola.

## ¿Qué es MD5?

MD5 (Message Digest Algorithm 5) es una función de hash criptográfica
diseñada por Ronald Rivest en 1991. Produce un hash de 128 bits
(representado como 32 caracteres hexadecimales) a partir de cualquier
input. Si bien hoy se considera inseguro para aplicaciones criptográficas
(vulnerable a colisiones desde 2004), sigue siendo útil para verificación
de integridad de datos y es un algoritmo de referencia para aprender
hashing en GPU.

## Implementación

### Algoritmo (RFC 1321)

El MD5 procesa el input en bloques de 512 bits siguiendo estos pasos:

1. **Padding**: se agrega un bit 1 (0x80) y ceros hasta que la longitud
   sea congruente a 448 mod 512. Luego se agrega la longitud original
   en 64 bits little-endian.
2. **Inicialización**: cuatro valores de 32 bits (a0, b0, c0, d0)
   definidos por el estándar.
3. **Procesamiento**: 64 rondas de operaciones sobre cada bloque de 512
   bits usando constantes K[] y desplazamientos S[].
4. **Output**: los cuatro valores finales concatenados forman el hash
   de 128 bits.

### Estructura del código

- `__constant__`: las constantes K[] y S[] se almacenan en memoria
  constante de la GPU para acceso rápido desde todos los hilos.
- `__device__ void md5()`: función que ejecuta el algoritmo MD5,
  llamada desde el kernel.
- `__global__ void md5Kernel()`: kernel CUDA que ejecuta el cálculo
  en la GPU con 1 bloque y 1 hilo.
- `cudaMalloc / cudaMemcpy / cudaFree`: gestión manual de memoria
  entre CPU y GPU.

Ver archivo: `md5_gpu.cu`

## Compilación y ejecución

```bash
nvcc --cudart shared md5_gpu.cu -o md5_gpu
./md5_gpu <string>
```

## Resultados obtenidos

| Input        | MD5 (CUDA GPU)                     | md5sum (referencia)                | Coincide |
| ------------ | ---------------------------------- | ---------------------------------- | -------- |
| `hello`      | `5d41402abc4b2a76b9719d911017c592` | `5d41402abc4b2a76b9719d911017c592` | ✓        |
| `blockchain` | `5510a843bc1b7acb9507a5f71de51b98` | `5510a843bc1b7acb9507a5f71de51b98` | ✓        |
| `hola mundo` | `0ad066a5d29f3f2a2a1c7c17dd082a79` | `0ad066a5d29f3f2a2a1c7c17dd082a79` | ✓        |

### Verificación

Los hashes fueron verificados usando `md5sum` dentro del contenedor:

```bash
echo -n "hello"      | md5sum  # 5d41402abc4b2a76b9719d911017c592
echo -n "blockchain" | md5sum  # 5510a843bc1b7acb9507a5f71de51b98
echo -n "hola mundo" | md5sum  # 0ad066a5d29f3f2a2a1c7c17dd082a79
```

El flag `-n` es fundamental para evitar que `echo` agregue un salto
de línea al final del string, lo que alteraría el hash resultante.

## Entorno

- Contenedor: srirajpaul/gpgpu-sim:0.2
- CUDA: 10.1
- Simulador: GPGPU-Sim 4.0.0
- Arquitectura simulada: sm_30
- Modo: functional simulation

## Conclusión

La implementación de MD5 en CUDA desde cero siguiendo el estándar
RFC 1321 produce resultados idénticos a la implementación de referencia
`md5sum` de Linux para todos los casos de prueba. Esto confirma que
el algoritmo está correctamente implementado y es la base para los
siguientes hits donde se usará fuerza bruta para encontrar nonces.
