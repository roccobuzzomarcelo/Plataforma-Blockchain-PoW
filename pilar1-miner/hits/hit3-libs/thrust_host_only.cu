#include <thrust/host_vector.h>
#include <thrust/sort.h>
#include <thrust/reduce.h>
#include <stdio.h>

int main() {
    // Thrust host_vector funciona en CPU sin problemas
    thrust::host_vector<int> h_vec(5);
    h_vec[0] = 50;
    h_vec[1] = 10;
    h_vec[2] = 40;
    h_vec[3] = 20;
    h_vec[4] = 30;

    printf("Vector original:\n");
    for (int i = 0; i < 5; i++) {
        printf("  h_vec[%d] = %d\n", i, h_vec[i]);
    }

    // Ordenar con Thrust
    thrust::sort(h_vec.begin(), h_vec.end());

    printf("\nVector ordenado con thrust::sort:\n");
    for (int i = 0; i < 5; i++) {
        printf("  h_vec[%d] = %d\n", i, h_vec[i]);
    }

    // Sumar todos los elementos con Thrust
    int suma = thrust::reduce(h_vec.begin(), h_vec.end(), 0);
    printf("\nSuma total con thrust::reduce: %d\n", suma);

    printf("\nThrust (host) funciona correctamente!\n");
    return 0;
}