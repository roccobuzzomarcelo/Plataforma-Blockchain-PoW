#include <stdio.h>
#include <string.h>
#include <stdint.h>

// =============================================
// Constantes del algoritmo MD5 (RFC 1321)
// =============================================
__constant__ uint32_t K[64] = {
    0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee,
    0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
    0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be,
    0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
    0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa,
    0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
    0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed,
    0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
    0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c,
    0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
    0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05,
    0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
    0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039,
    0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
    0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1,
    0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391
};

__constant__ uint32_t S[64] = {
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5,  9, 14, 20, 5,  9, 14, 20, 5,  9, 14, 20, 5,  9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
};

// =============================================
// Macro de rotación a izquierda
// =============================================
#define LEFTROTATE(x, c) (((x) << (c)) | ((x) >> (32 - (c))))

// =============================================
// Función MD5 ejecutada en la GPU
// =============================================
__device__ void md5(const uint8_t *input, uint32_t length, uint8_t *digest) {
    // Valores iniciales del hash (RFC 1321)
    uint32_t a0 = 0x67452301;
    uint32_t b0 = 0xefcdab89;
    uint32_t c0 = 0x98badcfe;
    uint32_t d0 = 0x10325476;

    // Buffer de padding (máximo 128 bytes para strings cortos)
    uint8_t msg[128];
    uint32_t new_len = length;

    // Copiar input al buffer
    for (uint32_t i = 0; i < length; i++) {
        msg[i] = input[i];
    }

    // Padding: agregar bit 1 (0x80)
    msg[new_len++] = 0x80;

    // Padding: agregar ceros hasta 56 mod 64
    while (new_len % 64 != 56) {
        msg[new_len++] = 0x00;
    }

    // Agregar longitud original en bits (64 bits, little-endian)
    uint64_t bit_len = (uint64_t)length * 8;
    for (int i = 0; i < 8; i++) {
        msg[new_len++] = (bit_len >> (i * 8)) & 0xFF;
    }

    // Procesar cada bloque de 512 bits (64 bytes)
    for (uint32_t offset = 0; offset < new_len; offset += 64) {
        uint32_t *w = (uint32_t *)(msg + offset);

        uint32_t a = a0, b = b0, c = c0, d = d0;

        for (int i = 0; i < 64; i++) {
            uint32_t f, g;
            if (i < 16) {
                f = (b & c) | (~b & d);
                g = i;
            } else if (i < 32) {
                f = (d & b) | (~d & c);
                g = (5 * i + 1) % 16;
            } else if (i < 48) {
                f = b ^ c ^ d;
                g = (3 * i + 5) % 16;
            } else {
                f = c ^ (b | ~d);
                g = (7 * i) % 16;
            }

            f = f + a + K[i] + w[g];
            a = d;
            d = c;
            c = b;
            b = b + LEFTROTATE(f, S[i]);
        }

        a0 += a;
        b0 += b;
        c0 += c;
        d0 += d;
    }

    // Escribir resultado en little-endian
    uint32_t *out = (uint32_t *)digest;
    out[0] = a0;
    out[1] = b0;
    out[2] = c0;
    out[3] = d0;
}

// =============================================
// Kernel CUDA: calcula MD5 del input
// =============================================
__global__ void md5Kernel(const uint8_t *input, uint32_t length, uint8_t *output) {
    // Solo usamos 1 hilo para este ejercicio
    if (threadIdx.x == 0 && blockIdx.x == 0) {
        md5(input, length, output);
    }
}

// =============================================
// Main: recibe string por parámetro
// =============================================
int main(int argc, char *argv[]) {
    if (argc < 2) {
        printf("Uso: %s <string>\n", argv[0]);
        printf("Ejemplo: %s \"hola mundo\"\n", argv[0]);
        return 1;
    }

    const char *input = argv[1];
    uint32_t length = strlen(input);

    printf("Input:  \"%s\"\n", input);
    printf("Largo:  %d bytes\n", length);

    // Alocar memoria en GPU
    uint8_t *d_input, *d_output;
    cudaMalloc(&d_input, length);
    cudaMalloc(&d_output, 16); // MD5 = 128 bits = 16 bytes

    // Copiar input a GPU
    cudaMemcpy(d_input, input, length, cudaMemcpyHostToDevice);

    // Ejecutar kernel con 1 bloque y 1 hilo
    md5Kernel<<<1, 1>>>(d_input, length, d_output);
    cudaDeviceSynchronize();

    // Copiar resultado de GPU a CPU
    uint8_t h_output[16];
    cudaMemcpy(h_output, d_output, 16, cudaMemcpyDeviceToHost);

    // Imprimir hash en formato hexadecimal
    printf("MD5:    ");
    for (int i = 0; i < 16; i++) {
        printf("%02x", h_output[i]);
    }
    printf("\n");

    // Liberar memoria
    cudaFree(d_input);
    cudaFree(d_output);

    return 0;
}