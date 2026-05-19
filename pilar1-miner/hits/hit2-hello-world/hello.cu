#include <stdio.h>

// __global__ indica que esta función se ejecuta en la GPU
__global__ void helloFromGPU() {
    printf("Hola Mundo desde la GPU! Bloque: %d, Hilo: %d\n",
           blockIdx.x, threadIdx.x);
}

int main() {
    printf("Hola Mundo desde la CPU!\n");

    // Lanzamos el kernel con 2 bloques y 4 hilos por bloque
    helloFromGPU<<<2, 4>>>();

    // Esperamos que la GPU termine antes de salir
    cudaDeviceSynchronize();

    printf("Fin del programa.\n");
    return 0;
}