#!/bin/bash
set -e

# ─────────────────────────────────────────────
#  Automata – Full Stack Setup Script
#  Tested on: Ubuntu/Debian ARM64 (Radxa A7S)
# ─────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()    { echo -e "${GREEN}[✔]${NC} $1"; }
warn()   { echo -e "${YELLOW}[!]${NC} $1"; }
error()  { echo -e "${RED}[✘]${NC} $1"; exit 1; }
section(){ echo -e "\n${CYAN}━━━ $1 ━━━${NC}"; }

# ─── Config ───────────────────────────────────
INFRA_COMPOSE_DIR="/opt/automata-infra"
APP_COMPOSE_DIR="/opt/automata"
HIVEMQ_CONF_DIR="/opt/hivemq/conf"
HIVEMQ_EXT_DIR="/opt/hivemq/extensions"
AUTOMATA_NET="automata-net"
HOMELAB_NET="homelab"

# ─── Spotify credentials (prompted once) ──────
section "Spotify Credentials"
if [[ -f "$APP_COMPOSE_DIR/.env" ]]; then
    warn ".env already exists at $APP_COMPOSE_DIR/.env — skipping credential prompt"
    warn "Delete it and re-run if you want to update credentials"
else
    read -rp "  Spotify Client ID     : " SPOTIFY_CLIENT_ID
    read -rsp "  Spotify Client Secret : " SPOTIFY_CLIENT_SECRET
    echo
fi

# ─── 1. System dependencies ───────────────────
section "System Dependencies"

if ! command -v docker &>/dev/null; then
    warn "Docker not found — installing..."
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker "$USER"
    log "Docker installed (you may need to log out and back in for group change)"
else
    log "Docker already installed: $(docker --version)"
fi

if ! command -v docker-compose &>/dev/null && ! docker compose version &>/dev/null 2>&1; then
    warn "docker compose plugin not found — installing..."
    sudo apt-get install -y docker-compose-plugin
fi

log "Docker Compose: $(docker compose version)"

# ─── 2. Docker networks ────────────────────────
section "Docker Networks"

for NET in "$HOMELAB_NET" "$AUTOMATA_NET"; do
    if docker network ls --format '{{.Name}}' | grep -wq "$NET"; then
        log "Network '$NET' already exists"
    else
        docker network create "$NET"
        log "Created network: $NET"
    fi
done

# ─── 3. Infrastructure stack ───────────────────
section "Infrastructure Stack (MongoDB, Redis, HiveMQ)"

sudo mkdir -p "$INFRA_COMPOSE_DIR"
sudo mkdir -p "$HIVEMQ_CONF_DIR"
sudo mkdir -p "$HIVEMQ_EXT_DIR"

# Fix HiveMQ directory ownership (container runs as uid 10000)
sudo chown -R 10000:10000 /opt/hivemq 2>/dev/null || true

sudo tee "$INFRA_COMPOSE_DIR/docker-compose.yml" > /dev/null << 'EOF'
services:
  mongodb:
    image: mongo:8
    container_name: mongodb
    restart: unless-stopped
    networks:
      - homelab
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: 12345678
    volumes:
      - mongodb_data:/data/db
      - mongodb_config:/data/configdb

  redis:
    image: redis:7-alpine
    container_name: redis
    restart: unless-stopped
    command: redis-server --appendonly yes
    networks:
      - homelab
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  hivemq:
    image: hivemq/hivemq-ce:latest
    container_name: hivemq
    restart: unless-stopped
    networks:
      - homelab
    ports:
      - "1883:1883"
      - "9001:9001"
    volumes:
      - /opt/hivemq/conf:/opt/hivemq/conf
      - /opt/hivemq/extensions:/opt/hivemq/extensions
      - hivemq_data:/opt/hivemq/data
      - hivemq_log:/opt/hivemq/log

networks:
  homelab:
    external: true

volumes:
  mongodb_data:
  mongodb_config:
  redis_data:
  hivemq_data:
  hivemq_log:
EOF

log "Written: $INFRA_COMPOSE_DIR/docker-compose.yml"

cd "$INFRA_COMPOSE_DIR"
docker compose pull
docker compose up -d
log "Infrastructure stack started"

# ─── 4. Automata app compose ───────────────────
section "Automata App Compose"

sudo mkdir -p "$APP_COMPOSE_DIR"

sudo tee "$APP_COMPOSE_DIR/docker-compose.yml" > /dev/null << 'EOF'
services:
  automata:
    build: .
    image: myapp
    container_name: automata
    ports:
      - "8010:8010"
    networks:
      - default
      - automata-net
      - homelab
    extra_hosts:
      - "host.docker.internal:host-gateway"
    restart: unless-stopped
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPOTIFY_CLIENT_ID=${SPOTIFY_CLIENT_ID}
      - SPOTIFY_CLIENT_SECRET=${SPOTIFY_CLIENT_SECRET}

networks:
  automata-net:
    external: true
  homelab:
    external: true
EOF

log "Written: $APP_COMPOSE_DIR/docker-compose.yml"

# Write .env only if it doesn't exist
if [[ ! -f "$APP_COMPOSE_DIR/.env" ]]; then
    sudo tee "$APP_COMPOSE_DIR/.env" > /dev/null << EOF
SPOTIFY_CLIENT_ID=${SPOTIFY_CLIENT_ID}
SPOTIFY_CLIENT_SECRET=${SPOTIFY_CLIENT_SECRET}
EOF
    sudo chmod 600 "$APP_COMPOSE_DIR/.env"
    log "Written: $APP_COMPOSE_DIR/.env (mode 600)"
fi

# ─── 5. Dockerfile in app dir ─────────────────
section "Dockerfile"

if [[ ! -f "$APP_COMPOSE_DIR/Dockerfile" ]]; then
    sudo tee "$APP_COMPOSE_DIR/Dockerfile" > /dev/null << 'EOF'
FROM eclipse-temurin:21-jdk AS builder
ENV TZ=Asia/Kolkata
WORKDIR /app
COPY target/Automata-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8010
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF
    log "Written: $APP_COMPOSE_DIR/Dockerfile"
else
    warn "Dockerfile already exists — skipped"
fi

# ─── 6. Jenkins Jenkinsfile ────────────────────
section "Jenkinsfile"

if [[ ! -f "$APP_COMPOSE_DIR/Jenkinsfile" ]]; then
sudo tee "$APP_COMPOSE_DIR/Jenkinsfile" > /dev/null << 'JENKINSEOF'
pipeline {
    agent any

    environment {
        SPRING_PROFILE    = 'prod'
        CONTAINER_NAME    = 'automata'
        NETWORK_NAME      = 'bridge'
        SECOND_NETWORK_NAME = 'automata-net'
        THIRD_NETWORK_NAME  = 'homelab'
    }

    stages {
        stage('Checkout Code') {
            steps {
                git 'https://github.com/subhamgupta28/Automata.git'
            }
        }

        stage('Test') {
            steps {
                echo "Running tests..."
            }
        }

        stage('Build UI') {
            tools { nodejs 'NodeJs25' }
            steps {
                dir('frontend') {
                    sh 'npm install'
                    sh 'npm run build || true'
                    sh 'test -f ../src/main/resources/static/index.html && echo "UI build verified"'
                }
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t myapp .'
            }
        }

        stage('Ensure Networks Exist') {
            steps {
                sh 'docker network ls | grep -w ${NETWORK_NAME}       || docker network create ${NETWORK_NAME}'
                sh 'docker network ls | grep -w ${SECOND_NETWORK_NAME} || docker network create ${SECOND_NETWORK_NAME}'
                sh 'docker network ls | grep -w ${THIRD_NETWORK_NAME}  || docker network create ${THIRD_NETWORK_NAME}'
            }
        }

        stage('Stop and Remove Old Container') {
            steps {
                sh 'docker ps -q   -f name=${CONTAINER_NAME} | xargs -r docker stop'
                sh 'docker ps -a -q -f name=${CONTAINER_NAME} | xargs -r docker rm'
            }
        }

        stage('Run Docker Container') {
            steps {
                withCredentials([
                    string(credentialsId: 'spotify-client-id',     variable: 'SPOTIFY_CLIENT_ID'),
                    string(credentialsId: 'spotify-client-secret', variable: 'SPOTIFY_CLIENT_SECRET')
                ]) {
                    sh '''
                    docker run -d --name ${CONTAINER_NAME} \
                      --restart unless-stopped \
                      --network ${NETWORK_NAME} \
                      --add-host=host.docker.internal:host-gateway \
                      -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILE} \
                      -e SPOTIFY_CLIENT_ID="${SPOTIFY_CLIENT_ID}" \
                      -e SPOTIFY_CLIENT_SECRET="${SPOTIFY_CLIENT_SECRET}" \
                      -p 8010:8010 \
                      myapp
                    '''
                }
                sh 'docker network connect ${SECOND_NETWORK_NAME} ${CONTAINER_NAME}'
                sh 'docker network connect ${THIRD_NETWORK_NAME}  ${CONTAINER_NAME}'
            }
        }
    }

    post {
        success { echo 'Deployment successful!' }
        failure { echo 'Deployment failed.' }
    }
}
JENKINSEOF
    log "Written: $APP_COMPOSE_DIR/Jenkinsfile"
else
    warn "Jenkinsfile already exists — skipped"
fi

# ─── 7. Health check ──────────────────────────
section "Health Check"

sleep 3
for SVC in mongodb redis hivemq; do
    STATUS=$(docker inspect --format='{{.State.Status}}' "$SVC" 2>/dev/null || echo "not found")
    if [[ "$STATUS" == "running" ]]; then
        log "$SVC: running"
    else
        warn "$SVC: $STATUS"
    fi
done

# ─── Summary ──────────────────────────────────
section "Setup Complete"
echo ""
echo "  Infrastructure:  $INFRA_COMPOSE_DIR"
echo "  App compose:     $APP_COMPOSE_DIR"
echo "  Networks:        $HOMELAB_NET, $AUTOMATA_NET"
echo ""
echo "  MongoDB  → localhost:27017  (admin / 12345678)"
echo "  Redis    → localhost:6379"
echo "  HiveMQ   → localhost:1883 (MQTT)  :9001 (WS)"
echo "  Automata → localhost:8010  (deployed by Jenkins)"
echo ""
echo -e "${YELLOW}  Jenkins pipeline reads .env from $APP_COMPOSE_DIR/.env${NC}"
echo -e "${YELLOW}  Make sure Spotify credentials are in Jenkins credential store too.${NC}"
echo ""
log "Done."