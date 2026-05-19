#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <time.h>

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

#define LEFTROTATE(x, c) (((x) << (c)) | ((x) >> (32 - (c))))

__device__ void md5(const uint8_t *input, uint32_t length, uint8_t *digest) {
    uint32_t a0 = 0x67452301;
    uint32_t b0 = 0xefcdab89;
    uint32_t c0 = 0x98badcfe;
    uint32_t d0 = 0x10325476;

    uint8_t msg[128];
    uint32_t new_len = length;

    for (uint32_t i = 0; i < length; i++) msg[i] = input[i];
    msg[new_len++] = 0x80;
    while (new_len % 64 != 56) msg[new_len++] = 0x00;

    uint32_t bit_len_lo = length * 8;
    uint32_t bit_len_hi = 0;
    for (int i = 0; i < 4; i++) msg[new_len++] = (bit_len_lo >> (i * 8)) & 0xFF;
    for (int i = 0; i < 4; i++) msg[new_len++] = (bit_len_hi >> (i * 8)) & 0xFF;

    for (uint32_t offset = 0; offset < new_len; offset += 64) {
        uint32_t *w = (uint32_t *)(msg + offset);
        uint32_t a = a0, b = b0, c = c0, d = d0;

        for (int i = 0; i < 64; i++) {
            uint32_t f, g;
            if (i < 16)      { f = (b & c) | (~b & d); g = i; }
            else if (i < 32) { f = (d & b) | (~d & c); g = (5*i+1)%16; }
            else if (i < 48) { f = b ^ c ^ d;           g = (3*i+5)%16; }
            else             { f = c ^ (b | ~d);         g = (7*i)%16; }

            f = f + a + K[i] + w[g];
            a = d; d = c; c = b;
            b = b + LEFTROTATE(f, S[i]);
        }
        a0 += a; b0 += b; c0 += c; d0 += d;
    }

    uint32_t *out = (uint32_t *)digest;
    out[0] = a0; out[1] = b0; out[2] = c0; out[3] = d0;
}

__device__ void byteToHex(uint8_t byte, char *out) {
    const char hex[] = "0123456789abcdef";
    out[0] = hex[(byte >> 4) & 0xF];
    out[1] = hex[byte & 0xF];
}

__device__ uint32_t buildInput(
    const char *base, uint32_t base_len,
    uint32_t nonce, uint8_t *out
) {
    for (uint32_t i = 0; i < base_len; i++) out[i] = base[i];

    char nonce_str[12];
    int nonce_len = 0;

    if (nonce == 0) {
        nonce_str[nonce_len++] = '0';
    } else {
        uint32_t tmp = nonce;
        while (tmp > 0) {
            nonce_str[nonce_len++] = '0' + (tmp % 10);
            tmp /= 10;
        }
        for (int i = 0; i < nonce_len / 2; i++) {
            char t = nonce_str[i];
            nonce_str[i] = nonce_str[nonce_len - 1 - i];
            nonce_str[nonce_len - 1 - i] = t;
        }
    }

    for (int i = 0; i < nonce_len; i++) out[base_len + i] = nonce_str[i];
    return base_len + nonce_len;
}

__global__ void rangeKernel(
    const char *base, uint32_t base_len,
    const char *prefix, uint32_t prefix_len,
    uint32_t start_nonce,
    uint32_t end_nonce,
    uint32_t *found_nonce,
    uint8_t  *found_hash,
    int      *found_flag
) {
    uint32_t nonce = start_nonce
                   + blockIdx.x * blockDim.x
                   + threadIdx.x;

    // Respetar el límite superior del rango
    if (nonce > end_nonce) return;
    if (*found_flag) return;

    uint8_t input[128];
    uint32_t input_len = buildInput(base, base_len, nonce, input);

    uint8_t digest[16];
    md5(input, input_len, digest);

    char hex[33];
    for (int i = 0; i < 16; i++) byteToHex(digest[i], &hex[i*2]);
    hex[32] = '\0';

    bool match = true;
    for (uint32_t i = 0; i < prefix_len; i++) {
        if (hex[i] != prefix[i]) { match = false; break; }
    }

    if (match) {
        if (atomicCAS(found_flag, 0, 1) == 0) {
            *found_nonce = nonce;
            for (int i = 0; i < 16; i++) found_hash[i] = digest[i];
        }
    }
}

int main(int argc, char *argv[]) {
    if (argc < 5) {
        printf("Uso: %s <cadena> <prefijo> <nonce_min> <nonce_max>\n", argv[0]);
        printf("Ejemplo: %s \"blockchain\" \"00\" 0 1000\n", argv[0]);
        return 1;
    }

    const char *base     = argv[1];
    const char *prefix   = argv[2];
    uint32_t nonce_min   = (uint32_t)atol(argv[3]);
    uint32_t nonce_max   = (uint32_t)atol(argv[4]);
    uint32_t base_len    = strlen(base);
    uint32_t prefix_len  = strlen(prefix);

    printf("Cadena:    \"%s\"\n", base);
    printf("Prefijo:   \"%s\" (%d caracteres)\n", prefix, prefix_len);
    printf("Rango:     [%u, %u]\n", nonce_min, nonce_max);
    printf("Buscando nonce en rango...\n\n");

    // Validar rango
    if (nonce_min > nonce_max) {
        printf("ERROR: nonce_min (%u) no puede ser mayor que nonce_max (%u)\n",
               nonce_min, nonce_max);
        return 1;
    }

    char *d_base, *d_prefix;
    uint32_t *d_found_nonce;
    uint8_t  *d_found_hash;
    int      *d_found_flag;

    cudaMalloc(&d_base,        base_len);
    cudaMalloc(&d_prefix,      prefix_len);
    cudaMalloc(&d_found_nonce, sizeof(uint32_t));
    cudaMalloc(&d_found_hash,  16);
    cudaMalloc(&d_found_flag,  sizeof(int));

    cudaMemcpy(d_base,   base,   base_len,   cudaMemcpyHostToDevice);
    cudaMemcpy(d_prefix, prefix, prefix_len, cudaMemcpyHostToDevice);
    cudaMemset(d_found_flag,  0, sizeof(int));
    cudaMemset(d_found_nonce, 0, sizeof(uint32_t));

    int threads_per_block = 256;
    int blocks            = 256;
    int batch_size        = threads_per_block * blocks;

    uint32_t current_start = nonce_min;
    int h_found_flag       = 0;

    clock_t t_start = clock();

    while (!h_found_flag && current_start <= nonce_max) {
        // El batch no puede superar nonce_max
        uint32_t current_end = current_start + batch_size - 1;
        if (current_end > nonce_max) current_end = nonce_max;

        rangeKernel<<<blocks, threads_per_block>>>(
            d_base, base_len,
            d_prefix, prefix_len,
            current_start,
            current_end,
            d_found_nonce,
            d_found_hash,
            d_found_flag
        );
        cudaDeviceSynchronize();
        cudaMemcpy(&h_found_flag, d_found_flag, sizeof(int), cudaMemcpyDeviceToHost);
        current_start += batch_size;
    }

    clock_t t_end = clock();
    double elapsed = (double)(t_end - t_start) / CLOCKS_PER_SEC;

    if (h_found_flag) {
        uint32_t h_found_nonce;
        uint8_t  h_found_hash[16];
        cudaMemcpy(&h_found_nonce, d_found_nonce, sizeof(uint32_t), cudaMemcpyDeviceToHost);
        cudaMemcpy(h_found_hash,   d_found_hash,  16,               cudaMemcpyDeviceToHost);

        printf("Nonce encontrado: %u\n", h_found_nonce);
        printf("MD5 resultante:   ");
        for (int i = 0; i < 16; i++) printf("%02x", h_found_hash[i]);
        printf("\n");
        printf("Prefijo buscado:  \"%s\" ✓\n", prefix);
    } else {
        printf("No se encontro nonce con prefijo \"%s\" en el rango [%u, %u]\n",
               prefix, nonce_min, nonce_max);
    }

    printf("Tiempo:           %.3f segundos\n", elapsed);

    cudaFree(d_base);
    cudaFree(d_prefix);
    cudaFree(d_found_nonce);
    cudaFree(d_found_hash);
    cudaFree(d_found_flag);

    return 0;
}