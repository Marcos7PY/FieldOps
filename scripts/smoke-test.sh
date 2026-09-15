#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

BASE_URL="${GATEWAY_URL:-http://localhost:8080}"
MAILHOG_URL="${MAILHOG_URL:-http://localhost:8025}"

fail() {
  local step="$1"
  local msg="$2"
  local body="${3:-}"
  echo "FAIL [Paso $step]: $msg" >&2
  if [ -n "$body" ]; then
    echo "Respuesta del servidor:" >&2
    echo "$body" >&2
  fi
  exit 1
}

# Helper para invocar jq o python si jq no está en PATH
query_json() {
  local query="$1"
  if command -v jq >/dev/null 2>&1; then
    jq -r "$query"
  else
    python -c "import sys, json; data=json.load(sys.stdin); print(eval('''$query''', {}, {'data': data}))"
  fi
}

echo "=== Ejecutando Smoke Test End-to-End ==="

# 1. POST /api/v1/auth/login con supervisor -> extrae accessToken y refreshToken, asserta HTTP 200
echo "[1/9] Autenticando como supervisor..."
LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"supervisor","password":"Demo2026!"}')
HTTP_CODE=$(echo "$LOGIN_RESP" | tail -n1)
BODY=$(echo "$LOGIN_RESP" | sed '$d')

if [ "$HTTP_CODE" -ne 200 ]; then
  fail "1" "HTTP code esperado 200 pero se recibió $HTTP_CODE" "$BODY"
fi

ACCESS_TOKEN=$(echo "$BODY" | query_json '.accessToken')
REFRESH_TOKEN=$(echo "$BODY" | query_json '.refreshToken')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  fail "1" "No se pudo extraer accessToken" "$BODY"
fi

# 2. GET /.well-known/jwks.json en el gateway -> asserta que hay al menos una clave con kty: RSA
echo "[2/9] Consultando JWKS en el gateway..."
JWKS_RESP=$(curl -s -w "\n%{http_code}" "$BASE_URL/.well-known/jwks.json")
HTTP_CODE=$(echo "$JWKS_RESP" | tail -n1)
BODY=$(echo "$JWKS_RESP" | sed '$d')

if [ "$HTTP_CODE" -ne 200 ]; then
  fail "2" "HTTP code esperado 200 en /.well-known/jwks.json pero se recibió $HTTP_CODE" "$BODY"
fi

RSA_KEYS=$(echo "$BODY" | query_json '[.keys[] | select(.kty == "RSA")] | length')
if [ -z "$RSA_KEYS" ] || [ "$RSA_KEYS" -lt 1 ]; then
  fail "2" "No se encontró ninguna clave con kty: RSA en JWKS" "$BODY"
fi

# 3. POST /api/v1/work-orders con el token de supervisor -> asserta 201 y captura id y ETag
echo "[3/9] Creando orden de trabajo como supervisor..."
HEADERS_FILE=$(mktemp)
CREATE_RESP=$(curl -s -w "\n%{http_code}" -D "$HEADERS_FILE" -X POST "$BASE_URL/api/v1/work-orders" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": 1,
    "title": "Mantenimiento Smoke Test",
    "description": "Orden creada automáticamente por smoke-test.sh",
    "priority": "HIGH",
    "scheduledDate": "2026-09-30T10:00:00"
  }')
HTTP_CODE=$(echo "$CREATE_RESP" | tail -n1)
BODY=$(echo "$CREATE_RESP" | sed '$d')

if [ "$HTTP_CODE" -ne 201 ]; then
  rm -f "$HEADERS_FILE"
  fail "3" "HTTP code esperado 201 en POST /api/v1/work-orders pero se recibió $HTTP_CODE" "$BODY"
fi

ORDER_ID=$(echo "$BODY" | query_json '.id')
ETAG=$( (grep -i '^etag:' "$HEADERS_FILE" || true) | tr -d '\r\n' | awk '{print $2}' | tr -d '"')
rm -f "$HEADERS_FILE"

if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" = "null" ]; then
  fail "3" "No se pudo extraer id de la orden creada" "$BODY"
fi
if [ -z "$ETAG" ]; then
  # Fallback a la propiedad version del body si el header ETag no viniera
  ETAG=$(echo "$BODY" | query_json '.version')
fi

# 4. PATCH /api/v1/work-orders/{id}/assign con If-Match válido -> asserta 200
echo "[4/9] Asignando orden de trabajo a tecnico1..."
ASSIGN_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "$BASE_URL/api/v1/work-orders/$ORDER_ID/assign" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "If-Match: \"$ETAG\"" \
  -H "Content-Type: application/json" \
  -d '{"technicianId": 2}')
HTTP_CODE=$(echo "$ASSIGN_RESP" | tail -n1)
BODY=$(echo "$ASSIGN_RESP" | sed '$d')

if [ "$HTTP_CODE" -ne 200 ]; then
  fail "4" "HTTP code esperado 200 en PATCH /assign pero se recibió $HTTP_CODE" "$BODY"
fi

ORDER_VERSION=$(echo "$BODY" | query_json '.version')

# 5. Login como tecnico1, PATCH .../status a IN_PROGRESS con If-Match -> asserta 200
echo "[5/9] Iniciando sesión como tecnico1 y pasando a IN_PROGRESS..."
TECH_LOGIN=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"tecnico1","password":"Demo2026!"}')
HTTP_CODE=$(echo "$TECH_LOGIN" | tail -n1)
BODY=$(echo "$TECH_LOGIN" | sed '$d')

if [ "$HTTP_CODE" -ne 200 ]; then
  fail "5" "No se pudo autenticar como tecnico1 (código $HTTP_CODE)" "$BODY"
fi

TECH_TOKEN=$(echo "$BODY" | query_json '.accessToken')

STATUS_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "$BASE_URL/api/v1/work-orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $TECH_TOKEN" \
  -H "If-Match: \"$ORDER_VERSION\"" \
  -H "Content-Type: application/json" \
  -d '{"newStatus":"IN_PROGRESS","notes":"Iniciando trabajo en campo"}')
HTTP_CODE=$(echo "$STATUS_RESP" | tail -n1)
BODY=$(echo "$STATUS_RESP" | sed '$d')

if [ "$HTTP_CODE" -ne 200 ]; then
  fail "5" "HTTP code esperado 200 al cambiar estado a IN_PROGRESS pero se recibió $HTTP_CODE" "$BODY"
fi

ORDER_VERSION=$(echo "$BODY" | query_json '.version')

# 6. POST .../evidence con un PNG de 1 px -> asserta 201
echo "[6/9] Subiendo evidencia fotográfica de 1px PNG..."
TEMP_PNG=$(mktemp --suffix=.png)
# 1x1 transparent PNG en base64
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=" | base64 -d > "$TEMP_PNG"
CURL_PNG="$(command -v cygpath >/dev/null && cygpath -w "$TEMP_PNG" || echo "$TEMP_PNG")"

EVIDENCE_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/work-orders/$ORDER_ID/evidence" \
  -H "Authorization: Bearer $TECH_TOKEN" \
  -F "file=@$CURL_PNG;type=image/png" \
  -F "notes=Evidencia 1px prueba smoke")
HTTP_CODE=$(echo "$EVIDENCE_RESP" | tail -n1)
BODY=$(echo "$EVIDENCE_RESP" | sed '$d')
rm -f "$TEMP_PNG"

if [ "$HTTP_CODE" -ne 201 ]; then
  fail "6" "HTTP code esperado 201 en subida de evidencia pero se recibió $HTTP_CODE" "$BODY"
fi

# 7. PATCH .../status a COMPLETED -> asserta 200
echo "[7/9] Completando orden de trabajo..."
COMP_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "$BASE_URL/api/v1/work-orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $TECH_TOKEN" \
  -H "If-Match: \"$ORDER_VERSION\"" \
  -H "Content-Type: application/json" \
  -d '{"newStatus":"COMPLETED","notes":"Trabajo finalizado con éxito"}')
HTTP_CODE=$(echo "$COMP_RESP" | tail -n1)
BODY=$(echo "$COMP_RESP" | sed '$d')

if [ "$HTTP_CODE" -ne 200 ]; then
  fail "7" "HTTP code esperado 200 al cambiar estado a COMPLETED pero se recibió $HTTP_CODE" "$BODY"
fi

# 8. Espera hasta 30 s y consulta GET http://localhost:8025/api/v2/messages (MailHog) -> asserta >= 2 correos
echo "[8/9] Esperando hasta 30s por notificaciones por correo en MailHog..."
MAIL_COUNT=0
for i in $(seq 1 15); do
  MH_RESP=$(curl -s "$MAILHOG_URL/api/v2/messages" || echo '{"total":0}')
  MAIL_COUNT=$(echo "$MH_RESP" | query_json '.total // 0')
  if [ "$MAIL_COUNT" -ge 2 ]; then
    break
  fi
  sleep 2
done

if [ "$MAIL_COUNT" -lt 2 ]; then
  fail "8" "Se esperaban al menos 2 correos en MailHog tras 30s, pero se encontraron $MAIL_COUNT" "$MH_RESP"
fi

# 9. GET /api/v1/analytics/metrics/daily como supervisor -> asserta 200 y que el conteo del día es >= 1
echo "[9/9] Verificando métricas diarias en analytics..."
METRICS_RESP=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/v1/analytics/metrics/daily" \
  -H "Authorization: Bearer $ACCESS_TOKEN")
HTTP_CODE=$(echo "$METRICS_RESP" | tail -n1)
BODY=$(echo "$METRICS_RESP" | sed '$d')

if [ "$HTTP_CODE" -ne 200 ]; then
  fail "9" "HTTP code esperado 200 en GET /api/v1/analytics/metrics/daily pero se recibió $HTTP_CODE" "$BODY"
fi

TOTAL_METRIC_COUNT=$(echo "$BODY" | query_json 'if .totalOrders != null then .totalOrders elif type=="array" then [.[].orderCount // 0] | add elif .metrics != null then [.metrics[].orderCount // 0] | add else .orderCount // 0 end')
if [ -z "$TOTAL_METRIC_COUNT" ] || [ "$TOTAL_METRIC_COUNT" -lt 1 ]; then
  fail "9" "Conteo de métricas diarias menor a 1" "$BODY"
fi

echo "SMOKE OK"
