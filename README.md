# Automata

A full‑stack automation and device orchestration platform. The backend is a Spring Boot (Java 21) service that integrates MongoDB, Redis, WebSockets, MQTT, and JWT-based auth. The frontend is a React app built with Vite and Material UI, bundled into Spring resources for production or served by Vite during development.

**📋 Architecture Documentation**: See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed system architecture, component diagrams, and data flow documentation.

Note: This README replaces stale info in the previous version (e.g., references to Jakarta EE and port 8080). It documents the current stack and provides setup/run instructions. Where details are unknown, TODOs are added.


## Overview

Automata is an enterprise-grade IoT automation platform that enables seamless device management, automation orchestration, and real-time data visualization. It provides a complete solution for managing smart devices, creating complex automation workflows, and monitoring system performance.

### Key Capabilities
- **Device Management**: Register, configure, and monitor smart devices (ESP32, Raspberry Pi, WLED)
- **Automation Engine**: Define rules with conditions and triggers, execute actions via MQTT
- **Real-time Communication**: WebSocket (STOMP) for browser updates, MQTT for device communication
- **Data Analytics**: Time-series data collection, analytics, and trend visualization
- **Dashboards**: Interactive virtual dashboards with customizable widgets
- **Notifications**: Real-time alerts and system notifications
- **Multi-tenant Ready**: Role-based access control with JWT authentication
- **System Monitoring**: Health checks, metrics, and performance analytics

### Core Features
- Real‑time device telemetry via MQTT with multi-topic support
- Live data streaming to connected clients via WebSocket (STOMP)
- Automation rules with condition evaluation and action execution
- Energy statistics and consumption tracking
- Interactive charts using Chart.js, Recharts, and MUI X-Charts
- Geolocation mapping with Leaflet
- Virtual device and dashboard creation
- System performance monitoring and analytics


## Technology Stack

### Backend (Spring Boot 3.3.x, Java 21)
- **Core Framework**: Spring Boot 3.3.1 with Spring Web MVC
- **Security**: Spring Security with JWT (JJWT 0.11.5) for authentication
- **Data Access**: Spring Data MongoDB for document storage
- **Caching**: Spring Data Redis and Spring Cache for performance optimization
- **Messaging**: 
  - WebSocket with STOMP protocol for real-time browser communication
  - Spring Integration MQTT 6.2.1 for IoT device communication
- **System Integration**: 
  - OSHI Core for system metrics collection
  - jMDNS for device discovery
  - Jackson for XML/JSON serialization
- **Monitoring**: Spring Boot Actuator for health checks and metrics
- **Async Processing**: Virtual threads enabled, @EnableAsync and @EnableScheduling
- **Build Tool**: Maven 3.9+ with Spring Boot Maven Plugin

### Frontend (React 18 with Vite)
- **Build Tool**: Vite 6.x for fast development and optimized production builds
- **UI Library**: Material-UI (MUI) 7.x with Emotion styling
- **Routing**: React Router DOM 7.x
- **HTTP Client**: Axios with custom interceptors for API communication
- **Real-time**: SockJS + STOMP for WebSocket communication
- **Visualization**:
  - Chart.js 4.x with react-chartjs-2
  - Recharts 2.x for responsive charts
  - MUI X-Charts for advanced charting
  - Google Charts integration
- **Mapping**: Leaflet 1.9.x with react-leaflet for geolocation
- **Animation**: Framer Motion, Motion library
- **UI State**: Notistack for notifications
- **Date Handling**: Day.js for temporal operations
- **Utilities**: React Flow for visual node-based UI

### Data Storage & Messaging
- **MongoDB**: Document database for flexible data storage
  - Device configurations and metadata
  - Time-series telemetry data (data_hist collection)
  - Automation rules and definitions
  - User profiles and preferences
  - Dashboard configurations
- **Redis**: In-memory caching and session store
  - Application cache with TTL
  - Session management
  - Real-time data state
- **MQTT Broker**: IoT device communication
  - HiveMQ CE or Mosquitto
  - Topics: automata/status, automata/action/{deviceId}, automata/sendData

### Infrastructure & DevOps
- **Containerization**: Docker with multi-stage builds
- **Orchestration**: Kubernetes with YAML manifests (Deployment, Service, ConfigMap, Secret)
- **CI/CD**: Jenkins for automated build and deployment pipelines
- **Version Control**: Git-based configuration management


## Project Structure

```
Automata/
├── pom.xml                              # Maven build configuration
├── Dockerfile                           # Container image definition
├── docker-compose.yaml                  # Local dev environment (MongoDB)
├── mvnw, mvnw.cmd                      # Maven wrapper for cross-platform builds
├── README.md                            # This file
├── ARCHITECTURE.md                      # Detailed system architecture documentation
├── architecture-diagram.txt             # ASCII architecture visualization
├── component-diagram.txt                # Component interaction diagram
├── data-flow-diagram.txt               # Data flow visualization
│
├── src/main/java/dev/automata/automata/
│   ├── AutomataApplication.java         # Spring Boot entry point
│   ├── WebConfig.java                   # CORS and HTTP configuration
│   ├── ServletInitializer.java          # WAR deployment support
│   │
│   ├── configs/                         # Configuration classes
│   │   ├── ApplicationConfiguration.java
│   │   ├── MqttConfig.java              # MQTT integration setup
│   │   ├── SecurityConfiguration.java   # JWT & security filters
│   │   └── ...
│   │
│   ├── controller/                      # REST API endpoints
│   │   ├── MainController.java          # Core device & data endpoints
│   │   ├── VirtualDeviceController.java # Virtual device management
│   │   ├── AutomationController.java    # Automation rules endpoints
│   │   ├── VirtualDashboardController.java
│   │   └── UtilityController.java
│   │
│   ├── service/                         # Business logic layer
│   │   ├── MainService.java             # Core operations
│   │   ├── MqttService.java             # MQTT messaging
│   │   ├── AutomationService.java       # Automation execution
│   │   ├── AnalyticsService.java        # Data analytics
│   │   ├── NotificationService.java     # Alerts & notifications
│   │   ├── VirtualDeviceService.java
│   │   ├── VirtualDashboardService.java
│   │   ├── RedisService.java
│   │   ├── ScheduleTasks.java           # Background scheduler
│   │   ├── UdpBroadcastService.java     # Device discovery
│   │   └── ...
│   │
│   ├── repository/                      # Data access layer
│   │   ├── DeviceRepository.java        # MongoDB device queries
│   │   ├── DataRepository.java          # Telemetry data queries
│   │   ├── AutomationRepository.java
│   │   ├── UserRepository.java
│   │   └── ...
│   │
│   ├── model/                           # Entity/Document classes
│   │   ├── Device.java                  # Device entity
│   │   ├── Data.java                    # Real-time data
│   │   ├── DataHist.java                # Time-series history
│   │   ├── Automation.java              # Automation rules
│   │   ├── AutomationDetail.java
│   │   ├── Users.java                   # User accounts
│   │   ├── Notification.java
│   │   ├── Dashboard.java
│   │   ├── VirtualDevice.java
│   │   ├── VirtualDashboard.java
│   │   ├── Attribute.java, AttributeType.java
│   │   ├── Status.java, Parameter.java
│   │   └── ...
│   │
│   ├── dto/                             # Data Transfer Objects
│   │   ├── DeviceDto.java
│   │   ├── DataDto.java
│   │   ├── ChartDataDto.java
│   │   ├── LiveEvent.java
│   │   └── ...
│   │
│   ├── mqtt/                            # MQTT integration
│   │   ├── MqttEventsListener.java      # Message handlers
│   │   └── SafeJsonTransformer.java
│   │
│   ├── modules/                         # Feature modules
│   │   ├── SystemMetrics.java           # System info collection
│   │   ├── Wled.java                    # WLED device integration
│   │   ├── AudioReactiveWled.java
│   │   ├── AudioWebSocketHandler.java
│   │   ├── DeviceDiscovery.java         # Network device discovery
│   │   └── ...
│   │
│   ├── security/                        # Security filters & handlers
│   │   └── JwtAuthenticationFilter.java
│   │
│   └── websocket/                       # WebSocket message handling
│
├── src/main/resources/
│   ├── application.properties            # Spring configuration
│   └── static/                          # Production frontend build (Vite output)
│
├── src/test/java/                       # Unit and integration tests
│
├── frontend/                            # React application (Vite)
│   ├── package.json                     # Dependencies and scripts
│   ├── vite.config.js                   # Vite build configuration
│   ├── index.html                       # HTML entry point
│   ├── public/                          # Static assets
│   │
│   └── src/
│       ├── main.jsx                     # React entry point
│       ├── App.jsx                      # Root component
│       ├── Theme.jsx                    # Material-UI theme
│       ├── App.css, index.css           # Global styles
│       │
│       ├── assets/
│       │   └── Icons.jsx                # Icon components
│       │
│       ├── components/
│       │   ├── Nav.jsx                  # Navigation
│       │   ├── Welcome.jsx              # Landing page
│       │   ├── Devices.jsx              # Device listing
│       │   ├── Notifications.jsx        # Notification center
│       │   │
│       │   ├── action/                  # Automation workflow editor
│       │   │   ├── ActionBoard.jsx      # Node-based editor
│       │   │   ├── ActionNode.jsx, ConditionNode.jsx, TriggerNode.jsx
│       │   │   ├── CustomEdge.jsx       # Flow connections
│       │   │   ├── CreateAction.jsx     # Action creation form
│       │   │   └── test.json
│       │   │
│       │   ├── auth/                    # Authentication UI
│       │   │   ├── AuthContext.jsx      # Auth context provider
│       │   │   ├── SignIn.jsx, SignUp.jsx
│       │   │   ├── ForgotPassword.jsx
│       │   │   └── PrivateRoute.jsx     # Protected route wrapper
│       │   │
│       │   ├── charts/                  # Chart components
│       │   │   ├── CustomLineChart.jsx, CustomBarChart.jsx
│       │   │   ├── CustomPieChart.jsx, CustomRadarChart.jsx
│       │   │   ├── GaugeChart.jsx, HumidityGauge.jsx, TemperatureGauge.jsx
│       │   │   ├── ChartDetail.jsx      # Chart details view
│       │   │   ├── MapView.jsx          # Leaflet map component
│       │   │   ├── PersonTracker.jsx
│       │   │   ├── TrafficChart.jsx
│       │   │   ├── Presets.jsx
│       │   │   └── ...
│       │   │
│       │   ├── dashboard/               # Dashboard components
│       │   ├── device_types/            # Device-specific UI
│       │   ├── integrations/            # Third-party integration UIs
│       │   ├── custom_drawer/           # Custom sidebar
│       │   ├── home/                    # Home page
│       │   ├── demo/                    # Demo components
│       │   └── v2/                      # Next-gen UI components
│       │
│       ├── services/
│       │   ├── apis.jsx                 # API endpoint definitions
│       │   ├── CustomAxios.jsx          # HTTP client with interceptors
│       │   ├── AppCacheContext.jsx      # State management
│       │   ├── DeviceDataProvider.jsx
│       │   ├── useWebSocket.jsx         # WebSocket hook
│       │   ├── HealthCheck.jsx
│       │   └── ...
│       │
│       ├── utils/
│       │   ├── Helper.jsx               # Utility functions
│       │   └── useIsMobile.jsx          # Responsive design hook
│       │
│       └── sass/
│           └── style.scss               # SCSS stylesheets
│
├── kubernetes-configs/                  # Kubernetes manifests
│   ├── automata-deployment.yaml         # Application deployment
│   ├── automata-service.yaml            # Service definition
│   ├── mongodb.yaml                     # MongoDB StatefulSet
│   ├── redis.yaml                       # Redis deployment
│   ├── mqtt.yaml                        # MQTT broker deployment
│   ├── configmap.yaml                   # Configuration
│   ├── secret.yaml                      # Secrets management
│   └── kube-remote-config               # Kubeconfig reference
│
├── build-steps.txt                      # Docker build pipeline steps
├── jenkins.txt                          # Jenkins CI/CD pipeline configuration
└── LINKEDIN_POST.md                     # Project announcement
```



### Development Environment
- **Java 21 JDK** (OpenJDK 21 or later)
- **Maven 3.9+** (for Java backend builds)
- **Node.js 18+ and npm** (for frontend development)
- **Git** (for version control)

### Runtime Dependencies
- **MongoDB 4.4+** (local installation or remote Atlas cluster)
  - Used for primary data persistence
  - Can be run via Docker: `docker run -d -p 27017:27017 mongo`
- **Redis 6.0+** (for caching and sessions)
  - Can be run via Docker: `docker run -d -p 6379:6379 redis`
  - Optional for development (falls back gracefully)
- **MQTT Broker** (HiveMQ CE or Mosquitto) for IoT device communication
  - Can be run via Docker: `docker run -d -p 1883:1883 eclipse-mosquitto`
  - Only required if using device integration features

### Optional Tools
- **Docker & Docker Compose** for containerized development
- **Kubernetes** (kubectl, cluster) for production deployment
- **Jenkins** for CI/CD pipelines
- **Postman/cURL** for API testing
- **VS Code or IntelliJ IDEA** for development


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


## Backend Setup & Running

### Configuration

**Application Properties** (src/main/resources/application.properties):
```properties
# Server
server.address=0.0.0.0
server.port=8010

# MongoDB
spring.data.mongodb.database=automata
spring.data.mongodb.host=192.168.1.54      # Change to your MongoDB host
spring.data.mongodb.port=27017
spring.data.mongodb.auto-index-creation=true

# Redis
spring.data.redis.host=192.168.1.54        # Change to your Redis host
spring.data.redis.port=6379
spring.cache.type=redis

# MQTT
application.mqtt.url=tcp://192.168.1.54:1883
application.mqtt.url_public=tcp://broker.hivemq.com:1883
application.mqtt.user=mqttadmin
application.mqtt.password=12345678

# JWT Security
application.security.jwt.secret-key=3cfa76ef14937c1c0ea519f8fc057a80fcd04a7420f8e8bcd0a7567c272e007b
application.security.jwt.expiration=604800000  # 7 days in milliseconds
application.security.jwt.refresh-token.expiration=604800000

# Timezone
spring.jackson.time-zone=Asia/Kolkata
```

**Update database credentials before running in production!**

### Development Mode

```bash
# Option 1: Using Maven Spring Boot plugin (hot reload)
cd d:\Projects\Automata
mvn clean spring-boot:run
# Application starts at http://localhost:8010

# Option 2: Build JAR then run
mvn clean package -DskipTests
java -jar target/Automata-0.0.1-SNAPSHOT.jar
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AutomataApplicationTests
```

### Health Check

Once running:
```bash
curl http://localhost:8010/api/v1/main/healthCheck
# Returns: "ok"
```

### API Documentation

The API follows RESTful conventions:
- Base URL: `http://localhost:8010/api/v1/`
- Controllers available:
  - **MainController** - Device data, charts, core operations
  - **VirtualDeviceController** - Virtual device CRUD
  - **AutomationController** - Automation rules and execution
  - **VirtualDashboardController** - Dashboard management
  - **UtilityController** - System utilities

Test endpoints using curl or Postman against `http://localhost:8010/api/v1/*`


## Frontend Setup & Running

### Development Mode

```bash
cd frontend

# Install dependencies
npm install

# Start dev server with hot reload
npm run dev
# Opens http://localhost:5173 by default
# Vite proxies API requests to http://localhost:8010/api/v1/
```

The frontend automatically detects environment and routes API calls:
- **Development** (Vite dev mode): `http://localhost:8010/api/v1/`
- **Production** (Spring-served): `http://{host}/api/v1/`

See [frontend/src/services/CustomAxios.jsx](frontend/src/services/CustomAxios.jsx) for API client configuration.

### Building for Production

```bash
cd frontend

# Build optimized production bundle
npm run build
# Output: ../src/main/resources/static/
# (Configured in vite.config.js, uses emptyOutDir to clean before build)

# Preview production build locally
npm run preview
# Serves built app at http://localhost:5173
```

### Available Scripts

```bash
npm run dev      # Start Vite dev server (port 5173)
npm run build    # Production build to Spring static resources
npm run preview  # Preview production build locally
npm run lint     # Run ESLint on codebase
```

### Frontend Access Points

- **Development**: http://localhost:5173 (Vite dev server)
- **Production**: http://localhost:8010 (Spring Boot serves static files)
- Both connect to backend API at `http://localhost:8010/api/v1/`

### Key Frontend Features

- **Real-time Updates**: WebSocket connection (STOMP) for live data
- **Device Management**: Add, configure, and monitor devices
- **Automation Builder**: Visual node-based workflow editor
- **Analytics**: Interactive charts and dashboards
- **Authentication**: Login/signup with JWT tokens
- **Responsive Design**: Mobile-friendly Material-UI components
- **Theme Support**: Light/dark mode with customizable theme

### Dependencies by Feature

- **Charting**: chart.js, recharts, @mui/x-charts, react-google-charts
- **Mapping**: leaflet, react-leaflet
- **Real-time**: sockjs-client, stompjs
- **HTTP**: axios, custom interceptors for auth
- **Forms**: React controlled components, Material-UI form inputs
- **State**: React Context API with hooks
- **Animation**: framer-motion, motion



## Docker & Container Setup

### Building Docker Image

```bash
# Build JAR with Maven (skip tests for faster build)
mvn clean package -DskipTests

# Build Docker image
docker build -t automata:latest .
# Or with a specific version tag:
docker build -t automata:v1.0 .

# Tag for Docker registry (e.g., Docker Hub or local registry)
docker tag automata:latest myregistry/automata:latest
```

### Running with Docker

```bash
# Run with required environment variables
docker run -d \
  --name automata \
  -p 8010:8010 \
  -e SPRING_DATA_MONGODB_HOST=host.docker.internal \
  -e SPRING_DATA_MONGODB_PORT=27017 \
  -e SPRING_DATA_MONGODB_DATABASE=automata \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e APPLICATION_MQTT_URL=tcp://host.docker.internal:1883 \
  -e APPLICATION_SECURITY_JWT_SECRET_KEY=your-secret-key \
  automata:latest
```

### Docker Compose (Local Development)

The provided [docker-compose.yaml](docker-compose.yaml) includes MongoDB:

```bash
# Start MongoDB service
docker-compose up -d mongodb

# Verify MongoDB is running
mongo --host localhost:27017
```

Currently, docker-compose includes only MongoDB. For a complete stack, you can extend it with Redis, MQTT, and the application:

```bash
# Stop services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

**To extend docker-compose for full stack**:
```yaml
version: "3.8"
services:
  mongodb:
    image: mongo:latest
    ports:
      - 27017:27017
    volumes:
      - mongo_data:/data/db
  
  redis:
    image: redis:7-alpine
    ports:
      - 6379:6379
  
  mqtt:
    image: eclipse-mosquitto:latest
    ports:
      - 1883:1883
    volumes:
      - mqtt_data:/mosquitto/data
  
  automata:
    build: .
    ports:
      - 8010:8010
    depends_on:
      - mongodb
      - redis
      - mqtt
    environment:
      SPRING_DATA_MONGODB_HOST: mongodb
      SPRING_DATA_REDIS_HOST: redis
      APPLICATION_MQTT_URL: tcp://mqtt:1883

volumes:
  mongo_data:
  mqtt_data:
```

Then run: `docker-compose up -d`

### Image Details

- **Base Image**: (See Dockerfile for JDK base)
- **Port**: 8010
- **Startup Command**: `java -jar Automata-0.0.1-SNAPSHOT.jar`
- **Healthcheck**: Available at `/api/v1/main/healthCheck`


## Kubernetes Deployment

### Available Manifests

The [kubernetes-configs/](kubernetes-configs/) directory contains:

```
kubernetes-configs/
├── automata-deployment.yaml     # Application Deployment with replicas, env vars
├── automata-service.yaml        # Service (ClusterIP) mapping port to targetPort 8010
├── mongodb.yaml                 # MongoDB StatefulSet for data persistence
├── redis.yaml                   # Redis Deployment for caching
├── mqtt.yaml                    # MQTT Broker Deployment
├── configmap.yaml               # ConfigMap for non-sensitive configuration
├── secret.yaml                  # Secret for sensitive data (DB creds, JWT keys)
└── kube-remote-config           # Kubeconfig reference for cluster access
```

### Deployment Steps

```bash
# 1. Create namespace (optional)
kubectl create namespace automata

# 2. Create secrets from sensitive data
kubectl apply -f kubernetes-configs/secret.yaml

# 3. Create ConfigMaps
kubectl apply -f kubernetes-configs/configmap.yaml

# 4. Deploy infrastructure (MongoDB, Redis, MQTT)
kubectl apply -f kubernetes-configs/mongodb.yaml
kubectl apply -f kubernetes-configs/redis.yaml
kubectl apply -f kubernetes-configs/mqtt.yaml

# 5. Deploy application
kubectl apply -f kubernetes-configs/automata-deployment.yaml
kubectl apply -f kubernetes-configs/automata-service.yaml

# 6. Verify deployment
kubectl get pods -l app=automata
kubectl get svc automata
```

### Service Configuration

**automata-service.yaml**: Maps external traffic to application
```yaml
apiVersion: v1
kind: Service
metadata:
  name: automata
spec:
  selector:
    app: automata
  ports:
    - protocol: TCP
      port: 6969           # External port
      targetPort: 8010     # Container port
  type: LoadBalancer
```

Access application at: `http://<LoadBalancer-IP>:6969`

### Deployment Configuration

**Key features in automata-deployment.yaml**:
- Configurable replicas for horizontal scaling
- Environment variables from ConfigMap and Secret
- Resource requests/limits
- Liveness and readiness probes
- ImagePullPolicy for registry management

### Managing Secrets

```bash
# Create secret for MongoDB credentials
kubectl create secret generic automata-secrets \
  --from-literal=mongo-username=rootuser \
  --from-literal=mongo-password=rootpass \
  --from-literal=mqtt-user=mqttadmin \
  --from-literal=mqtt-password=12345678

# View secrets
kubectl get secrets
kubectl describe secret automata-secrets

# Update secret
kubectl delete secret automata-secrets
kubectl create secret generic automata-secrets ...
```

### Accessing the Application

```bash
# Port-forward for local access
kubectl port-forward svc/automata 8010:6969
# Now accessible at http://localhost:8010

# Check logs
kubectl logs -f deployment/automata

# Shell into pod for debugging
kubectl exec -it <pod-name> -- /bin/bash
```

### Scaling

```bash
# Scale replicas
kubectl scale deployment automata --replicas=3

# Watch scaling in progress
kubectl get pods -w
```

### Resource Management

See [automata-deployment.yaml](kubernetes-configs/automata-deployment.yaml) for:
- Memory requests: Configured per environment
- CPU limits: Optimized for performance
- Storage claims: Persistent volumes for databases
- Image registry: Update image reference as needed


## CI/CD & Automation

### Build Pipeline

The [build-steps.txt](build-steps.txt) documents the complete build flow:
1. Maven compilation and testing
2. Docker image creation
3. Registry push
4. Deployment orchestration

### Jenkins Pipeline

The [jenkins.txt](jenkins.txt) contains a complete Jenkins pipeline that:
1. **Build Stage**: Compile JAR with Maven
2. **Test Stage**: Run unit tests
3. **Docker Stage**: Build Docker image tagged as `myapp`
4. **Network Stage**: Ensure Docker networks exist
5. **Deploy Stage**: Run container with proper environment

**Pipeline Configuration**:
```groovy
// Key stages:
// - Checkout: Pull source code
// - Build: mvn clean package
// - Image: docker build -t myapp:${BUILD_NUMBER} .
// - Push: docker push <registry>/myapp:${BUILD_NUMBER}
// - Deploy: kubectl apply -f kubernetes-configs/
```

**To use Jenkins pipeline**:
1. Create new Pipeline job in Jenkins
2. Point to Jenkinsfile or paste pipeline from jenkins.txt
3. Configure credentials for Docker registry and Kubernetes
4. Trigger builds on Git push (webhook)

### Local Development Testing

**REST API Testing**:
```bash
# Using curl
curl -X GET http://localhost:8010/api/v1/main/healthCheck

# Using Postman
# Import endpoints from controller classes
# Base URL: http://localhost:8010/api/v1/
```

**WebSocket Testing**:
```javascript
// From browser console or test client
const socket = new SockJS('http://localhost:8010/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({}, function() {
  stompClient.subscribe('/topic/notifications', function(msg) {
    console.log('Received:', msg.body);
  });
});
```

**MQTT Testing**:
```bash
# Using mosquitto_sub (if Mosquitto installed)
mosquitto_sub -h localhost -t "automata/status"

# Using MQTT.js CLI
npm install -g mqtt
mqtt sub -u mqttadmin -P 12345678 -h localhost -t 'automata/status'
```

### Continuous Integration Best Practices

1. **Always run tests** in CI pipeline
2. **Use semantic versioning** for releases (v1.0.0)
3. **Tag Docker images** with build number and git commit
4. **Use ConfigMaps/Secrets** for environment-specific config
5. **Implement health checks** for pod readiness
6. **Monitor deployment logs** for errors
7. **Plan rollback strategy** for failed deployments


## Application Access Points

### Development Environment

| Service | URL | Port | Purpose |
|---------|-----|------|---------|
| Backend API | http://localhost:8010 | 8010 | REST API endpoints |
| Frontend UI | http://localhost:5173 | 5173 | Vite dev server |
| MongoDB | localhost | 27017 | Database (local) |
| Redis | localhost | 6379 | Cache store (local) |
| MQTT Broker | localhost | 1883 | Device messaging (local) |
| Health Check | http://localhost:8010/api/v1/main/healthCheck | 8010 | System status |

### Production Environment (Spring-bundled)

When frontend is built and bundled into Spring resources:
- All routes served from Spring at `http://localhost:8010/`
- Frontend assets in `src/main/resources/static/`
- Backend API at `/api/v1/*` on same origin
- No CORS issues in production

**Vite Build Output**:
- Command: `npm run build` (in frontend/)
- Output directory: `src/main/resources/static/`
- Configured in: `frontend/vite.config.js`
- Result: Single executable JAR with embedded frontend

### Kubernetes Ingress (Production)

When deployed to Kubernetes:
- Service port: 6969 (external) → 8010 (internal)
- LoadBalancer IP: Check with `kubectl get svc automata`
- Access at: `http://<LoadBalancer-IP>:6969`


## Support & Documentation

### Getting Help

- **Architecture Questions**: See [ARCHITECTURE.md](ARCHITECTURE.md)
- **Component Diagrams**: See [architecture-diagram.txt](architecture-diagram.txt), [component-diagram.txt](component-diagram.txt), [data-flow-diagram.txt](data-flow-diagram.txt)
- **API Endpoints**: Check MainController, VirtualDeviceController classes in src/main/java
- **Configuration**: Edit src/main/resources/application.properties
- **Frontend Development**: See frontend/README.md

### Common Issues

**MongoDB connection refused**:
- Ensure MongoDB is running: `mongod` or via Docker
- Check host/port in application.properties
- Verify MongoDB credentials if auth enabled

**MQTT connection failed**:
- Verify MQTT broker is running
- Check MQTT credentials in application.properties
- Test with: `mosquitto_sub -h localhost -t "automata/#"`

**WebSocket connection issues**:
- Check browser console for connection errors
- Verify backend is running on correct port
- Check CORS configuration in WebConfig.java

**Frontend not loading**:
- In dev: Ensure Vite dev server running (`npm run dev`)
- In production: Verify frontend built and bundled (`npm run build`)
- Check Spring static resource serving

**JWT token expired**:
- Log out and log back in to refresh token
- Token expiration set in application.properties (default 7 days)
- Refresh token mechanism implemented in AuthContext.jsx

## License

No explicit license is declared in pom.xml or LICENSE file exists.
- **TODO**: Add a LICENSE file (e.g., MIT/Apache-2.0) and update pom.xml accordingly.

## Project Status

**Version**: 0.0.1-SNAPSHOT (Early Development)

**Latest Updates**:
- Spring Boot 3.3.1 with Java 21
- React 18 with Vite 6 build system
- Comprehensive MongoDB + Redis integration
- Production-ready Kubernetes manifests
- Jenkins CI/CD pipeline support

**Active Features**:
- Device management and MQTT communication
- Automation engine with condition-based triggers
- Real-time dashboard with WebSocket updates
- Analytics and charts
- User authentication with JWT

**Future Roadmap**:
- Mobile application support
- Advanced machine learning analytics
- Multi-tenant architecture
- API Gateway integration
- Enhanced monitoring with Prometheus/Grafana

## Changelog

**2026-03-27**
- Comprehensive README update with complete project structure
- Added detailed architecture documentation
- Updated all tech stack details with accurate versions
- Enhanced setup instructions for all components
- Added Kubernetes deployment guide
- Added CI/CD and Jenkins pipeline documentation
- Included troubleshooting section

**2025-10-04**
- Initial README covering Spring Boot 3 (port 8010), React+Vite, Mongo/Redis/MQTT stack
- Added setup, run, Docker, Kubernetes, env vars, scripts, tests, and TODOs for unknowns.
