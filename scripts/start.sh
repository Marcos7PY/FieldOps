#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export COMPOSE_FILE="$(command -v cygpath >/dev/null && cygpath -w "$ROOT/infra/docker/docker-compose.yml" || echo "$ROOT/infra/docker/docker-compose.yml")"

echo "=== Iniciando entorno FieldOps ==="

cd "$ROOT/infra/docker"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Archivo .env creado desde la plantilla .env.example"
fi

# Generar contraseñas de BD aisladas por microservicio si no están fijadas (F2-T08)
for svc in orders auth notifications analytics; do
  VAR="$(echo "$svc" | tr '[:lower:]' '[:upper:]')_DB_PASSWORD"
  MIG_VAR="$(echo "$svc" | tr '[:lower:]' '[:upper:]')_MIGRATOR_PASSWORD"
  for v in "$VAR" "$MIG_VAR"; do
    if ! grep -q "^${v}=." .env; then
      PASS="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)Aa1!"
      if grep -q "^${v}=" .env; then
        sed -i "s|^${v}=.*|${v}=${PASS}|" .env
      else
        echo "${v}=${PASS}" >> .env
      fi
    fi
  done
done

# Token de servicio interno (auth-service <-> notification-service)
if ! grep -q "^INTERNAL_SERVICE_TOKEN=." .env; then
  TOKEN="$(openssl rand -hex 32)"
  if grep -q "^INTERNAL_SERVICE_TOKEN=" .env; then
    sed -i "s|^INTERNAL_SERVICE_TOKEN=.*|INTERNAL_SERVICE_TOKEN=${TOKEN}|" .env
  else
    echo "INTERNAL_SERVICE_TOKEN=${TOKEN}" >> .env
  fi
fi

source .env

if [ ! -f "$ROOT/keys/private.pem" ]; then
  mkdir -p "$ROOT/keys"
  openssl genrsa -out "$ROOT/keys/private.pem" 2048
  openssl rsa -in "$ROOT/keys/private.pem" -pubout -out "$ROOT/keys/public.pem"
  echo "Par de claves RSA generado en $ROOT/keys/"
else
  echo "Claves RSA ya existentes"
fi

echo "Levantando infraestructura base (SQL Server, Kafka KRaft, Schema Registry, AKHQ, MailHog)..."
docker compose up -d sqlserver kafka schema-registry akhq mailhog

echo "Esperando a que la infraestructura esté lista..."
for service in sqlserver kafka schema-registry; do
  count=0
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "fieldops-$service" 2>/dev/null)" = "healthy" ] || [ $count -ge 40 ]; do
    sleep 3
    count=$((count + 1))
  done
  if [ "$(docker inspect -f '{{.State.Health.Status}}' "fieldops-$service" 2>/dev/null)" != "healthy" ]; then
    echo "ERROR: $service no alcanzó el estado healthy tras el tiempo de espera."
    exit 1
  fi
  echo "  $service listo"
done

echo "Verificando y creando bases de datos relacionales y usuarios por servicio..."
SQLCMD_BIN="/opt/mssql-tools/bin/sqlcmd"
if ! docker compose exec -T sqlserver test -x "$SQLCMD_BIN" 2>/dev/null; then
  SQLCMD_BIN="/opt/mssql-tools18/bin/sqlcmd"
fi

docker compose exec -T sqlserver "$SQLCMD_BIN" \
  -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C \
  -v AUTH_DB_PASSWORD="$AUTH_DB_PASSWORD" \
     AUTH_MIGRATOR_PASSWORD="${AUTH_MIGRATOR_PASSWORD:-$AUTH_DB_PASSWORD}" \
     ORDERS_DB_PASSWORD="$ORDERS_DB_PASSWORD" \
     ORDERS_MIGRATOR_PASSWORD="${ORDERS_MIGRATOR_PASSWORD:-$ORDERS_DB_PASSWORD}" \
     NOTIFICATIONS_DB_PASSWORD="$NOTIFICATIONS_DB_PASSWORD" \
     NOTIFICATIONS_MIGRATOR_PASSWORD="${NOTIFICATIONS_MIGRATOR_PASSWORD:-$NOTIFICATIONS_DB_PASSWORD}" \
     ANALYTICS_DB_PASSWORD="$ANALYTICS_DB_PASSWORD" \
     ANALYTICS_MIGRATOR_PASSWORD="${ANALYTICS_MIGRATOR_PASSWORD:-$ANALYTICS_DB_PASSWORD}" \
  -i /dev/stdin < "$ROOT/infra/sql/00_create_databases.sql"
echo "Bases de datos y usuarios verificados."

cd "$ROOT"
./scripts/create-topics.sh

echo "Levantando los cinco microservicios en contenedores..."
cd "$ROOT/infra/docker"
docker compose up -d auth-service orders-service notification-service analytics-service api-gateway

echo "Esperando disponibilidad de los microservicios vía healthchecks..."
for service in auth-service orders-service notification-service analytics-service api-gateway; do
  count=0
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "fieldops-$service" 2>/dev/null)" = "healthy" ] || [ $count -ge 60 ]; do
    sleep 2
    count=$((count + 1))
  done
  if [ "$(docker inspect -f '{{.State.Health.Status}}' "fieldops-$service" 2>/dev/null)" != "healthy" ]; then
    echo "ERROR: $service no alcanzó el estado healthy tras 120 segundos."
    exit 1
  fi
  echo "  $service listo"
done
echo "Microservicios verificados."

echo "Aplicando datos de demostración en fieldops_orders..."
docker compose exec -T sqlserver "$SQLCMD_BIN" \
  -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C \
  -i /dev/stdin < "$ROOT/infra/sql/01_seed_demo_data.sql"
echo "Datos de demostración aplicados."

echo
echo "=== Sistema FieldOps listo para operar ==="
echo "  API Gateway:          http://localhost:8080"
echo "  AKHQ (Kafka Web):     http://localhost:8091"
echo "  MailHog (SMTP Web):   http://localhost:8025"
echo
echo "Credenciales de demostración:"
echo "  Supervisor: supervisor / Demo2026! (supervisor@fieldops.com)"
echo "  Técnico 1:  tecnico1   / Demo2026! (tecnico1@fieldops.com)"
echo "  Técnico 2:  tecnico2   / Demo2026! (tecnico2@fieldops.com)"
echo "  Técnico 3:  tecnico3   / Demo2026! (tecnico3@fieldops.com)"