#!/bin/bash

# ==============================================
# run_benchmarks.sh - Ejecuta batería completa
# de tests y guarda resultados en CSV
# ==============================================

MINER_JAR="../cpu-miner/target/cpu-miner-1.0.0-SNAPSHOT.jar"
OUTPUT_FILE="results/benchmark_cpu_$(date +%Y%m%d_%H%M%S).csv"

mkdir -p results

echo "cadena,prefijo,longitud_prefijo,nonce,hash,tiempo_seg" > $OUTPUT_FILE

echo "================================================"
echo "  Benchmark CPU - Minero Java 21"
echo "  Output: $OUTPUT_FILE"
echo "================================================"
echo ""

run_case() {
    local cadena=$1
    local prefijo=$2

    output=$(java -jar $MINER_JAR "$cadena" "$prefijo" 2>/dev/null)
    nonce=$(echo "$output"  | grep "Nonce encontrado" | awk '{print $NF}')
    hash=$(echo "$output"   | grep "MD5 resultante"   | awk '{print $NF}')
    tiempo=$(echo "$output" | grep "Tiempo"           | awk '{print $NF}' | tr ',' '.')
    longitud=${#prefijo}

    if [ -z "$nonce" ]; then
        echo "[--] $cadena | prefijo=$prefijo | No encontrado"
        echo "$cadena,$prefijo,$longitud,,-," >> $OUTPUT_FILE
    else
        echo "[OK] $cadena | prefijo=$prefijo | nonce=$nonce | tiempo=${tiempo}seg"
        echo "$cadena,$prefijo,$longitud,$nonce,$hash,$tiempo" >> $OUTPUT_FILE
    fi
}

# Casos blockchain
run_case "blockchain" "0"
run_case "blockchain" "00"
run_case "blockchain" "000"
run_case "blockchain" "0000"
run_case "blockchain" "00000"

# Casos hello
run_case "hello" "0"
run_case "hello" "00"
run_case "hello" "000"
run_case "hello" "0000"
run_case "hello" "00000"

# Casos unlu2026
run_case "unlu2026" "0"
run_case "unlu2026" "00"
run_case "unlu2026" "000"
run_case "unlu2026" "0000"
run_case "unlu2026" "00000"

echo ""
echo "================================================"
echo "  Benchmark completo. Resultados en $OUTPUT_FILE"
echo "================================================"