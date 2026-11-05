#!/usr/bin/env bash
# One-shot local deployment: JWT keys -> .env -> build images -> start MySQL + both services.
#
# Requirements: JDK 21, Docker with the compose plugin, openssl.
# Usage: ./quickstart.sh
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

for cmd in docker openssl java; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "Error: '$cmd' is required but not installed." >&2; exit 1; }
done

# 1. JWT key pairs (kept if they already exist)
./keygen.sh

# 2. Environment file with random passwords (kept if it already exists)
if [[ ! -f .env ]]; then
  echo "Creating .env with random database passwords"
  sed \
    -e "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)|" \
    -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 24)|" \
    .env.example > .env
  chmod 600 .env
else
  echo "Using existing .env"
fi

# 3. Build both modules and their container images (dashboard/dashboard-*:1.0-SNAPSHOT)
./mvnw -B clean package -DskipTests -Dquarkus.container-image.build=true

# 4. Start the stack and wait until every service is healthy
docker compose up -d --wait

cat <<'EOF'

Dashboard is up:
  dashboard-security (token endpoint)  http://localhost:8080/jwt
  dashboard-user     (API)             http://localhost:8081
  health                               http://localhost:8081/q/health

Logs: docker compose logs -f    Stop: docker compose down
EOF
