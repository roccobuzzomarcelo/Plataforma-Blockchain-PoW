#include <thrust/host_vector.h>
#include <thrust/device_vector.h>
#include <stdio.h>

int main() {
    thrust::host_vector<int> h_vec(5);
    h_vec[0] = 10;
    h_vec[1] = 20;
    h_vec[2] = 30;
    h_vec[3] = 40;
    h_vec[4] = 50;

    printf("Vector en CPU:\n");
    for (int i = 0; i < 5; i++) {
        printf("  h_vec[%d] = %d\n", i, h_vec[i]);
    }

    // Transferencia CPU → GPU
    thrust::device_vector<int> d_vec = h_vec;
    printf("\nVector copiado a GPU exitosamente.\n");

    // Transferencia GPU → CPU
    thrust::host_vector<int> h_result = d_vec;
    printf("\nVector recuperado de GPU:\n");
    for (int i = 0; i < 5; i++) {
        printf("  h_result[%d] = %d\n", i, h_result[i]);
    }

    printf("\nThrust device_vector funciona correctamente!\n");
    return 0;
}