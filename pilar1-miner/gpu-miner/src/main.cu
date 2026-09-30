// Minero CUDA para la Plataforma Blockchain PoW -version "worker real",
// derivada de hits/hit7-range-limits/range_miner.cu (mismo MD5 en GPU, ya
// validado y funcionando).
//
// DIFERENCIA CLAVE con el Hit #7, y la razon de por que este archivo
// existe aparte: el sistema real no hashea "cadena + nonce" con una sola
// cadena. Hashea:
//
//     md5( str(nonce) + str + bcContent )
//
// con "str" y "bcContent" como dos campos SEPARADOS que ya vienen armados
// en cada MiningTask (ver HashUtils.powHash en el coordinator, y
// PoWMiner.java del lado Java). Nonce va PRIMERO, no al final, y son dos
// cadenas, no una. Verificado contra un bloque real del cluster antes de
// escribir esto (genesis, blockHash 3321d73c7bd9bd5b1e52b60ae70bf84c).
//
// Uso:
//   nvcc --cudart shared main.cu -o gpu_worker_miner
//   ./gpu_worker_miner "<str>" "<bcContent>" "<prefijo>" <nonce_min> <nonce_max>
//
// Salida (una sola linea, para que un script la parsee facil):
//   FOUND <nonce> <hash_hex> <segundos>
//   NOTFOUND <segundos>

#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <time.h>

// Tamano maximo de "str" + "bcContent" combinados. Alcanza para bloques
// de varias docenas de transacciones; para el escenario de carga de la
// 3.3 con miles de transacciones (bulk test) hay que agrandar esto y
// volver a compilar -no esta pensado para ese caso.
#define MAX_INPUT 8192

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

// MD5 de un mensaje de longitud arbitraria (multi-bloque). El Hit #7
// original solo soportaba un unico bloque de 64 bytes (buffer msg[128]
// con un solo "for offset" que en la practica corria una vez); acá el
// mensaje real (nonce+str+bcContent) puede superar los 55 bytes sin
// problema, así que el padding y el loop de bloques de 64 bytes tienen
// que ser genuinamente multi-bloque. Se reescribe esa parte con cuidado.
__device__ void md5(const uint8_t *input, uint32_t length, uint8_t *digest) {
    uint32_t a0 = 0x67452301;
    uint32_t b0 = 0xefcdab89;
    uint32_t c0 = 0x98badcfe;
    uint32_t d0 = 0x10325476;

    uint8_t msg[MAX_INPUT + 72];
    uint32_t new_len = length;

    for (uint32_t i = 0; i < length; i++) msg[i] = input[i];
    msg[new_len++] = 0x80;
    while (new_len % 64 != 56) msg[new_len++] = 0x00;

    uint64_t bit_len = (uint64_t)length * 8;
    for (int i = 0; i < 8; i++) msg[new_len++] = (bit_len >> (i * 8)) & 0xFF;

    for (uint32_t offset = 0; offset < new_len; offset += 64) {
        uint32_t w[16];
        for (int i = 0; i < 16; i++) {
            w[i] = (uint32_t)msg[offset + i*4]
                 | ((uint32_t)msg[offset + i*4 + 1] << 8)
                 | ((uint32_t)msg[offset + i*4 + 2] << 16)
                 | ((uint32_t)msg[offset + i*4 + 3] << 24);
        }
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

// Arma "str(nonce) + str_field + bc_field" -en ese orden, nonce primero-
// tal como lo hace HashUtils.powHash del lado Java.
__device__ uint32_t buildInput(
    uint64_t nonce,
    const char *str_field, uint32_t str_len,
    const char *bc_field,  uint32_t bc_len,
    uint8_t *out
) {
    char nonce_str[20];
    int nonce_len = 0;

    if (nonce == 0) {
        nonce_str[nonce_len++] = '0';
    } else {
        uint64_t tmp = nonce;
        while (tmp > 0) {
            nonce_str[nonce_len++] = '0' + (int)(tmp % 10);
            tmp /= 10;
        }
        for (int i = 0; i < nonce_len / 2; i++) {
            char t = nonce_str[i];
            nonce_str[i] = nonce_str[nonce_len - 1 - i];
            nonce_str[nonce_len - 1 - i] = t;
        }
    }

    uint32_t pos = 0;
    for (int i = 0; i < nonce_len; i++) out[pos++] = nonce_str[i];
    for (uint32_t i = 0; i < str_len; i++) out[pos++] = str_field[i];
    for (uint32_t i = 0; i < bc_len; i++)  out[pos++] = bc_field[i];
    return pos;
}

__global__ void rangeKernel(
    const char *str_field, uint32_t str_len,
    const char *bc_field,  uint32_t bc_len,
    const char *prefix,    uint32_t prefix_len,
    uint64_t start_nonce,
    uint64_t end_nonce,
    uint64_t *found_nonce,
    uint8_t  *found_hash,
    int      *found_flag
) {
    uint64_t nonce = start_nonce
                   + (uint64_t)blockIdx.x * blockDim.x
                   + threadIdx.x;

    if (nonce > end_nonce) return;
    if (*found_flag) return;

    uint8_t input[MAX_INPUT];
    uint32_t input_len = buildInput(nonce, str_field, str_len, bc_field, bc_len, input);

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
    if (argc < 6) {
        fprintf(stderr, "Uso: %s <str> <bcContent> <prefijo> <nonce_min> <nonce_max>\n", argv[0]);
        return 1;
    }

    const char *str_field = argv[1];
    const char *bc_field  = argv[2];
    const char *prefix    = argv[3];
    uint64_t nonce_min    = strtoull(argv[4], NULL, 10);
    uint64_t nonce_max    = strtoull(argv[5], NULL, 10);
    uint32_t str_len      = strlen(str_field);
    uint32_t bc_len       = strlen(bc_field);
    uint32_t prefix_len   = strlen(prefix);

    if (str_len + bc_len + 20 > MAX_INPUT) {
        fprintf(stderr, "ERROR: str+bcContent (%u bytes) supera MAX_INPUT (%d). Agrandar MAX_INPUT y recompilar.\n",
                str_len + bc_len, MAX_INPUT);
        return 1;
    }
    if (nonce_min > nonce_max) {
        fprintf(stderr, "ERROR: nonce_min (%llu) > nonce_max (%llu)\n",
                (unsigned long long)nonce_min, (unsigned long long)nonce_max);
        return 1;
    }

    char *d_str, *d_bc, *d_prefix;
    uint64_t *d_found_nonce;
    uint8_t  *d_found_hash;
    int      *d_found_flag;

    cudaMalloc(&d_str,         str_len);
    cudaMalloc(&d_bc,          bc_len);
    cudaMalloc(&d_prefix,      prefix_len);
    cudaMalloc(&d_found_nonce, sizeof(uint64_t));
    cudaMalloc(&d_found_hash,  16);
    cudaMalloc(&d_found_flag,  sizeof(int));

    cudaMemcpy(d_str,    str_field, str_len,    cudaMemcpyHostToDevice);
    cudaMemcpy(d_bc,     bc_field,  bc_len,     cudaMemcpyHostToDevice);
    cudaMemcpy(d_prefix, prefix,    prefix_len, cudaMemcpyHostToDevice);
    cudaMemset(d_found_flag,  0, sizeof(int));
    cudaMemset(d_found_nonce, 0, sizeof(uint64_t));

    int threads_per_block = 256;
    int blocks            = 256;
    uint64_t batch_size   = (uint64_t)threads_per_block * blocks;

    uint64_t current_start = nonce_min;
    int h_found_flag       = 0;

    clock_t t_start = clock();

    while (!h_found_flag && current_start <= nonce_max) {
        uint64_t current_end = current_start + batch_size - 1;
        if (current_end > nonce_max) current_end = nonce_max;

        rangeKernel<<<blocks, threads_per_block>>>(
            d_str, str_len, d_bc, bc_len,
            d_prefix, prefix_len,
            current_start, current_end,
            d_found_nonce, d_found_hash, d_found_flag
        );
        cudaDeviceSynchronize();
        cudaMemcpy(&h_found_flag, d_found_flag, sizeof(int), cudaMemcpyDeviceToHost);
        current_start += batch_size;
    }

    clock_t t_end = clock();
    double elapsed = (double)(t_end - t_start) / CLOCKS_PER_SEC;

    if (h_found_flag) {
        uint64_t h_found_nonce;
        uint8_t  h_found_hash[16];
        cudaMemcpy(&h_found_nonce, d_found_nonce, sizeof(uint64_t), cudaMemcpyDeviceToHost);
        cudaMemcpy(h_found_hash,   d_found_hash,  16,               cudaMemcpyDeviceToHost);

        char hex[33];
        for (int i = 0; i < 16; i++) {
            const char h[] = "0123456789abcdef";
            hex[i*2]   = h[(h_found_hash[i] >> 4) & 0xF];
            hex[i*2+1] = h[h_found_hash[i] & 0xF];
        }
        hex[32] = '\0';

        printf("FOUND %llu %s %.3f\n", (unsigned long long)h_found_nonce, hex, elapsed);
    } else {
        printf("NOTFOUND %.3f\n", elapsed);
    }

    cudaFree(d_str);
    cudaFree(d_bc);
    cudaFree(d_prefix);
    cudaFree(d_found_nonce);
    cudaFree(d_found_hash);
    cudaFree(d_found_flag);

    return 0;
}
