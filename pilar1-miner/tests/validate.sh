#!/bin/bash

# ==============================================
# validate.sh - Valida outputs del minero CPU
# Verifica que los hashes encontrados sean
# correctos usando md5sum como referencia
# ==============================================

MINER_JAR="../cpu-miner/target/cpu-miner-1.0.0-SNAPSHOT.jar"
PASS=0
FAIL=0

echo "================================================"
echo "  Validación de outputs - Minero CPU"
echo "================================================"
echo ""

validate() {
    local cadena=$1
    local prefijo=$2
    local rango_min=$3
    local rango_max=$4

    # Ejecutar minero y capturar nonce y hash
    output=$(java -jar $MINER_JAR "$cadena" "$prefijo" "$rango_min" "$rango_max" 2>/dev/null)
    nonce=$(echo "$output" | grep "Nonce encontrado" | awk '{print $NF}')
    hash_cuda=$(echo "$output" | grep "MD5 resultante" | awk '{print $NF}')

    if [ -z "$nonce" ]; then
        echo "[SKIP] $cadena + prefijo=$prefijo → No encontrado en rango [$rango_min, $rango_max]"
        return
    fi

    # Verificar con md5sum
    input="${cadena}${nonce}"
    hash_ref=$(echo -n "$input" | md5sum | awk '{print $1}')

    if [ "$hash_cuda" == "$hash_ref" ]; then
        echo "[OK]   $cadena$nonce → $hash_cuda"
        PASS=$((PASS + 1))
    else
        echo "[FAIL] $cadena$nonce"
        echo "       Esperado: $hash_ref"
        echo "       Obtenido: $hash_cuda"
        FAIL=$((FAIL + 1))
    fi
}

# Casos de validación con rangos conocidos
validate "blockchain" "0"     0 1000
validate "blockchain" "00"    0 1000
validate "blockchain" "000"   0 5000
validate "hello"      "0"     0 1000
validate "hello"      "00"    0 5000
validate "hello"      "000"   0 20000
validate "unlu2026"   "0"     0 1000
validate "unlu2026"   "00"    0 1000
validate "unlu2026"   "000"   0 10000

echo ""
echo "================================================"
echo "  Resultado: $PASS OK  |  $FAIL FAIL"
echo "================================================"