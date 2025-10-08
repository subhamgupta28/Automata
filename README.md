# Automata

A full‑stack automation and device orchestration platform. The backend is a Spring Boot (Java 21) service that integrates MongoDB, Redis, WebSockets, MQTT, and JWT-based auth. The frontend is a React app built with Vite and Material UI, bundled into Spring resources for production or served by Vite during development.

Note: This README replaces stale info in the previous version (e.g., references to Jakarta EE and port 8080). It documents the current stack and provides setup/run instructions. Where details are unknown, TODOs are added.


## Overview
Automata lets you:
- Manage devices and visualize status and telemetry
- Define automations with conditions and actions
- Send real-time actions to devices via WebSockets and MQTT
- Receive notifications for automation events

Key features (from codebase):
- Device Management and Automation orchestration
- Real‑time updates via STOMP over WebSocket and MQTT topics
- Notifications (alerts, app notifications)
- Charts and dashboards (Chart.js, Recharts)
- Maps/geolocation (Leaflet)


## Tech Stack
- Backend
  - Java 21, Spring Boot 3.3.x
  - Spring Web, WebSocket, Security (JWT), Data MongoDB, Data Redis, Cache, Actuator
  - MQTT via spring-integration-mqtt
  - Build: Maven, Spring Boot Maven Plugin
- Frontend
  - React 18, Vite 6, Material UI, Axios
  - Charts: chart.js, react-chartjs-2, recharts
  - Maps: leaflet, react-leaflet
  - Routing: react-router-dom
- Datastores & Messaging
  - MongoDB
  - Redis (cache and app state)
  - MQTT broker


## Requirements
- Java 21 (JDK)
- Maven 3.9+
- Node.js 18+ and npm
- MongoDB (local or remote)
- Redis (optional locally, enabled via Spring cache config)
- MQTT broker (e.g., HiveMQ / Mosquitto) if using MQTT features
- Docker (optional for container builds) and Docker Compose (optional)


## Project Structure
- pom.xml — Backend build and dependencies
- Dockerfile — Container entrypoint for Spring Boot JAR (port 8010)
- docker-compose.yaml — MongoDB service (data volume)
- kubernetes-configs/automata-service.yaml — K8s Service mapping port 6969 -> targetPort 8010
- src/main/java/dev/automata/automata/AutomataApplication.java — Spring Boot entry point
- src/main/resources/application.properties — Spring configuration
- src/main/resources/static — Production frontend bundle output (from Vite)
- frontend/ — React app (Vite)
  - package.json — scripts and dependencies
  - vite.config.js — build output path, API mode define
  - src/services/CustomAxios.jsx — API base URL logic for dev/prod


## Backend Service
- Entry point: dev.automata.automata.AutomataApplication
- Default host/port: 0.0.0.0:8010 (see application.properties)
- Notable integrations (from pom.xml):
  - WebSocket: spring-boot-starter-websocket
  - Security/JWT: spring-boot-starter-security, jjwt
  - Mongo: spring-boot-starter-data-mongodb
  - Redis: spring-boot-starter-data-redis, spring-boot-starter-cache
  - MQTT: spring-integration-mqtt
  - Actuator enabled

Run (development):
- From repo root:
  - mvn spring-boot:run
- Or package and run:
  - mvn clean package
  - java -jar target/Automata-0.0.1-SNAPSHOT.jar

Common Maven scripts:
- Build: mvn clean package
- Run: mvn spring-boot:run
- Tests: mvn test


## Frontend App (Vite + React)
Dev server:
- cd frontend
- npm install
- npm run dev
- Opens http://localhost:5173 by default

API base URL handling (see frontend/src/services/CustomAxios.jsx):
- In Vite dev (command === 'serve'): http://localhost:8010/api/v1/
- In production (built and served by Spring): http://{host}/api/v1/

Build for production (bundled into Spring resources):
- cd frontend
- npm run build
- Output goes to ../src/main/resources/static (configured in vite.config.js)

Available npm scripts (from package.json):
- npm run dev — Vite dev server
- npm run build — Production build (empties outDir)
- npm run preview — Preview built app
- npm run lint — ESLint



## Running with Docker
Build JAR and image:
- mvn clean package -DskipTests
- docker build -t automata:local .

Run container:
- docker run -d --name automata -p 8010:8010 \
  -e APPLICATION_SECURITY_JWT_SECRET_KEY=change-me \
  -e SPRING_DATA_MONGODB_HOST=host.docker.internal \
  -e SPRING_DATA_MONGODB_PORT=27017 \
  automata:local

Note: Provide Redis and MQTT envs if those integrations are enabled in your environment.

Compose (MongoDB only is defined in repo):
- docker-compose up -d mongodb
- TODO: extend docker-compose.yaml to include app (automata), redis, and mqtt services.


## Kubernetes
- A Service manifest exists at kubernetes-configs/automata-service.yaml mapping port 6969 to targetPort 8010 and selector app=automata.
- TODO: add a Deployment manifest for the app (image, env vars, liveness/readiness probes).
- TODO: add MongoDB/Redis/MQTT manifests or use managed services.
- TODO: use ConfigMap/Secret for properties (JWT secret, DB creds, MQTT creds).


## Scripts and CI/CD
- build-steps.txt shows a simple local build/push flow to a registry.
- jenkins.txt contains a Jenkins pipeline example that builds the JAR, builds a Docker image (myapp), ensures networks, and runs the container on port 8010.
- TODO: align image name/tag across Dockerfile, build steps, and Jenkins (currently uses myapp).


Manual/Integration testing:
- Use curl/Postman against http://localhost:8010/api/v1/* during dev
- WebSocket endpoints (STOMP) via SockJS from the frontend


## Access Points
- Backend (dev): http://localhost:8010
- Frontend (dev): http://localhost:5173
- In production build, the frontend is served by Spring from / (static resources under src/main/resources/static)


## License
No explicit license is declared in pom.xml (empty license stanza) and no LICENSE file exists.
- TODO: Add a LICENSE file (e.g., MIT/Apache-2.0) and update pom.xml <licenses> accordingly.


## Changelog
2025-10-04
- Rewrote README to reflect Spring Boot 3 on port 8010, React+Vite frontend, Mongo/Redis/MQTT usage.
- Added setup, run, Docker, Kubernetes, env vars, scripts, tests, and TODOs for unknowns.
