#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE="$ROOT/infra/docker/docker-compose.yml"

echo "=== Iniciando entorno FieldOps ==="

cd "$ROOT/infra/docker"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Archivo .env creado desde la plantilla .env.example"
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
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "fieldops-$service" 2>/dev/null)" = "healthy" ]; do
    sleep 3
  done
  echo "  $service listo"
done

echo "Verificando y creando bases de datos relacionales..."
docker compose exec -T sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C \
  -i /dev/stdin < "$ROOT/infra/sql/00_create_databases.sql"
echo "Bases de datos verificadas."

cd "$ROOT"
./scripts/create-topics.sh

echo "Levantando los cinco microservicios en contenedores..."
cd "$ROOT/infra/docker"
docker compose up -d auth-service orders-service notification-service analytics-service api-gateway

echo "Esperando disponibilidad de los microservicios..."
for port in 8081 8082 8083 8084 8080; do
  count=0
  until curl -sf "http://localhost:$port/actuator/health" >/dev/null 2>&1 || curl -sf "http://localhost:$port" >/dev/null 2>&1 || [ $count -ge 30 ]; do
    sleep 2
    count=$((count + 1))
  done
done
echo "Microservicios verificados."

echo "Aplicando datos de demostración en fieldops_orders..."
docker compose exec -T sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C \
  -i /dev/stdin < "$ROOT/infra/sql/01_seed_demo_data.sql"
echo "Datos de demostración aplicados."

echo
echo "=== Sistema FieldOps listo para operar ==="
echo "  API Gateway:          http://localhost:8080"
echo "  Auth Service:         http://localhost:8081"
echo "  Orders Service:       http://localhost:8082"
echo "  Notification Service: http://localhost:8083"
echo "  Analytics Service:    http://localhost:8084"
echo "  AKHQ (Kafka Web):     http://localhost:8091"
echo "  MailHog (SMTP Web):   http://localhost:8025"
echo
echo "Credenciales de demostración:"
echo "  Supervisor: supervisor / Demo2026! (supervisor@fieldops.com)"
echo "  Técnico 1:  tecnico1   / Demo2026! (tecnico1@fieldops.com)"
echo "  Técnico 2:  tecnico2   / Demo2026! (tecnico2@fieldops.com)"
echo "  Técnico 3:  tecnico3   / Demo2026! (tecnico3@fieldops.com)"