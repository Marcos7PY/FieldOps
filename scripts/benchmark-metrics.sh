#!/usr/bin/env bash
# ==============================================================================
# FieldOps: scripts/benchmark-metrics.sh
# Comparador de rendimiento: GET /work-orders/metrics vs GET /analytics/metrics/daily
# Ejecuta 50 peticiones consecutivas a cada endpoint calculando p50 y p95
# ==============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${AUTH_TOKEN:-}"
ITERATIONS=50

if [ -z "$TOKEN" ]; then
    echo "Obteniendo token JWT de autenticación..."
    AUTH_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"supervisor","password":"Demo2026!"}')
    TOKEN=$(echo "$AUTH_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
fi

if [ -z "$TOKEN" ]; then
    echo "ERROR: no se pudo obtener el token de autenticación. Respuesta:"
    echo "$AUTH_RESP"
    exit 1
fi

echo "Iniciando benchmark (50 peticiones por endpoint)..."
echo "--------------------------------------------------------"

run_benchmark() {
    local endpoint="$1"
    local name="$2"
    local times=()

    echo "Midiendo ${name} (${endpoint})..."
    for i in $(seq 1 $ITERATIONS); do
        t=$(curl -s -w "%{time_total}\n" -o /dev/null -X GET "${BASE_URL}${endpoint}" \
            -H "Authorization: Bearer ${TOKEN}")
        # Convertir a milisegundos con awk
        ms=$(awk "BEGIN {print int($t * 1000)}")
        times+=("$ms")
    done

    # Ordenar array numéricamente
    IFS=$'\n' sorted=($(sort -n <<<"${times[*]}"))
    unset IFS

    # Cálculo de índices p50 (mediana) y p95
    idx_p50=$(( (ITERATIONS * 50) / 100 ))
    idx_p95=$(( (ITERATIONS * 95) / 100 ))

    p50="${sorted[$idx_p50]}"
    p95="${sorted[$idx_p95]}"
    min="${sorted[0]}"
    max="${sorted[$((ITERATIONS - 1))]}"

    echo "Resultados para ${name}:"
    echo "  Muestras: $ITERATIONS"
    echo "  Min: ${min} ms | Max: ${max} ms"
    echo "  p50 (Mediana): ${p50} ms"
    echo "  p95: ${p95} ms"
    echo "--------------------------------------------------------"
}

FROM_DATE="${FROM_DATE:-2026-01-01T00:00:00}"
TO_DATE="${TO_DATE:-2026-04-01T00:00:00}"

run_benchmark "/api/v1/work-orders/metrics/range?from=${FROM_DATE}&to=${TO_DATE}" "SQL Optimizado (Transaccional)"
run_benchmark "/api/v1/analytics/metrics/daily" "Proyección de Eventos (Read Model CQRS)"
