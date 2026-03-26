# Automata - Architecture Documentation

## Overview

Automata is a full-stack IoT automation and device orchestration platform that enables users to manage smart devices, create automation workflows, and visualize real-time data through interactive dashboards. The system provides real-time communication via WebSockets and MQTT, supports JWT-based authentication, and offers comprehensive device management capabilities.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │   Web Browser   │    │   Mobile App    │    │   MQTT Client   │             │
│  │   (React SPA)   │    │   (Future)      │    │   (Devices)     │             │
│  │                 │    │                 │    │                 │             │
│  │ - Material UI   │    │                 │    │ - ESP32         │             │
│  │ - Chart.js      │    │                 │    │ - Raspberry Pi  │             │
│  │ - Leaflet Maps  │    │                 │    │ - Smart Devices │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   │ HTTP/WebSocket
                                   │ MQTT
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            APPLICATION LAYER                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                        SPRING BOOT APPLICATION                         │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │   │
│  │  │   Controllers   │  │   Services      │  │   Repositories  │         │   │
│  │  │                 │  │                 │  │                 │         │   │
│  │  │ - MainController│  │ - MainService   │  │ - DeviceRepo    │         │   │
│  │  │ - DeviceCtrl    │  │ - MqttService   │  │ - DataRepo      │         │   │
│  │  │ - AutomationCtrl│  │ - AnalyticsSvc  │  │ - AutomationRepo│         │   │
│  │  │ - AuthCtrl      │  │ - NotificationSvc│  │ - UserRepo      │         │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘         │   │
│  │                                                                         │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │   │
│  │  │   WebSocket     │  │   MQTT          │  │   Security      │         │   │
│  │  │   Handler       │  │   Integration   │  │   (JWT)         │         │   │
│  │  │ - STOMP         │  │ - Paho Client   │  │ - Auth Filters  │         │   │
│  │  │ - Real-time     │  │ - Topics        │  │ - CORS          │         │   │
│  │  │   Updates       │  │ - Pub/Sub       │  │                 │         │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘         │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             DATA LAYER                                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │    MongoDB      │    │     Redis       │    │   MQTT Broker  │             │
│  │                 │    │                 │    │                 │             │
│  │ - Device Data   │    │ - Cache         │    │ - HiveMQ        │             │
│  │ - Automations   │    │ - Sessions      │    │ - Mosquitto     │             │
│  │ - Time Series   │    │ - App State     │    │                 │             │
│  │ - User Data     │    │                 │    │                 │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           INFRASTRUCTURE LAYER                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │    Docker       │    │   Kubernetes    │    │    Jenkins      │             │
│  │                 │    │                 │    │                 │             │
│  │ - Container     │    │ - Deployments   │    │ - CI/CD         │             │
│  │ - Images        │    │ - Services      │    │ - Pipelines     │             │
│  │ - Compose       │    │ - ConfigMaps    │    │ - Automation    │             │
│  │                 │    │ - Secrets       │    │                 │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Component Details

### 1. Client Layer

#### Web Application (React + Vite)
- **Framework**: React 18 with Vite build tool
- **UI Library**: Material-UI (MUI) with Emotion styling
- **Charts**: Chart.js, Recharts, MUI X-Charts
- **Maps**: Leaflet with React-Leaflet
- **Routing**: React Router DOM
- **State Management**: React Context API
- **HTTP Client**: Axios with custom interceptors
- **Real-time**: SockJS + STOMP for WebSocket communication

#### IoT Devices
- **Protocols**: MQTT for device communication
- **Supported Devices**: ESP32, Raspberry Pi, WLED controllers
- **Topics**: 
  - `automata/status` - Device status updates
  - `automata/action/{deviceId}` - Action commands
  - `automata/sendData` - Telemetry data

### 2. Application Layer (Spring Boot)

#### Core Components
- **Framework**: Spring Boot 3.3.x with Java 21
- **Architecture**: Layered architecture (Controller → Service → Repository)
- **Configuration**: Application.properties with environment-specific settings

#### Controllers
- **MainController**: Core API endpoints for devices, data, and health checks
- **VirtualDeviceController**: Virtual device management
- **AutomationController**: Automation workflow management
- **VirtualDashboardController**: Dashboard configuration
- **UtilityController**: System utilities and maintenance

#### Services
- **MainService**: Core business logic for device operations
- **MqttService**: MQTT communication handling
- **AnalyticsService**: Data analytics and chart generation
- **NotificationService**: Push notifications and alerts

#### Real-time Communication
- **WebSocket**: STOMP over WebSocket for browser communication
- **MQTT**: Eclipse Paho client for IoT device communication
- **Topics**: 
  - Status updates
  - Action commands
  - Live data streaming

#### Security
- **Authentication**: JWT-based authentication
- **Authorization**: Spring Security with role-based access
- **CORS**: Configured for cross-origin requests
- **Secrets**: Environment variables and Kubernetes secrets

### 3. Data Layer

#### MongoDB
- **Purpose**: Primary data store for application data
- **Collections**:
  - Devices: Device metadata and configuration
  - Data/DataHist: Time-series telemetry data
  - Automations: Automation rules and workflows
  - Users: User accounts and preferences
  - Notifications: System notifications
  - Dashboards: Dashboard configurations

#### Redis
- **Purpose**: Caching and session management
- **Usage**:
  - Application caching (@EnableCaching)
  - Session storage
  - Temporary data storage
  - Pub/Sub for internal messaging

#### MQTT Broker
- **Purpose**: IoT device communication
- **Configuration**: HiveMQ or Mosquitto
- **Topics**: Device-specific topics for commands and status

### 4. Infrastructure Layer

#### Containerization
- **Docker**: Application containerization
- **Docker Compose**: Local development environment
- **Multi-stage Build**: Optimized production images

#### Orchestration
- **Kubernetes**: Production deployment
- **Resources**:
  - Deployment: Application pods
  - Service: Load balancing and networking
  - ConfigMap: Configuration management
  - Secret: Sensitive data management
  - PersistentVolume: Data persistence

#### CI/CD
- **Jenkins**: Automated build and deployment pipelines
- **Build Steps**: Maven compilation, testing, Docker image creation
- **Deployment**: Kubernetes rolling updates

## Data Flow

### Device Data Ingestion
1. IoT devices publish telemetry data to MQTT topics
2. MQTT integration layer receives messages
3. Data is processed and stored in MongoDB
4. Real-time updates sent via WebSocket to connected clients
5. Cached data maintained in Redis for performance

### User Interactions
1. Users interact with React frontend
2. API requests sent to Spring Boot controllers
3. Business logic executed in services
4. Data retrieved from MongoDB/Redis
5. Responses returned with real-time updates via WebSocket

### Automation Execution
1. Automation rules defined in UI
2. Rules stored in MongoDB
3. Background scheduler monitors conditions
4. When conditions met, actions triggered via MQTT
5. Notifications sent to users via WebSocket

## Security Architecture

### Authentication & Authorization
- JWT tokens for session management
- Refresh token mechanism
- Role-based access control
- CORS configuration for web clients

### Data Protection
- Environment variables for sensitive configuration
- Kubernetes secrets for production deployments
- Encrypted communication (HTTPS/WebSocket Secure)

### Network Security
- Container network isolation
- Service mesh considerations (future)
- API rate limiting (future)

## Scalability Considerations

### Horizontal Scaling
- Stateless application design
- External session storage (Redis)
- Database connection pooling
- Kubernetes horizontal pod autoscaling

### Performance Optimization
- Redis caching layer
- Database indexing
- Asynchronous processing (@EnableAsync)
- Virtual threads (Java 21)

### Monitoring & Observability
- Spring Boot Actuator endpoints
- Health checks and metrics
- Application logging
- Performance monitoring

## Deployment Architecture

### Development Environment
- Local Docker Compose setup
- Hot reload for frontend (Vite)
- Spring Boot dev tools
- Local MongoDB and Redis instances

### Production Environment
- Kubernetes cluster deployment
- Container registry for images
- Load balancer for traffic distribution
- Persistent volumes for data
- Secrets management

### CI/CD Pipeline
- Source code management (Git)
- Automated testing (JUnit)
- Build automation (Maven)
- Container image creation
- Deployment to Kubernetes

## Technology Stack Summary

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| Backend | Spring Boot | 3.3.x | REST API & Business Logic |
| Language | Java | 21 | Application Development |
| Frontend | React | 18 | User Interface |
| Build Tool | Vite | 6.x | Frontend Build & Dev Server |
| UI Library | Material-UI | 7.x | Component Library |
| Database | MongoDB | Latest | Primary Data Store |
| Cache | Redis | Latest | Caching & Sessions |
| Messaging | MQTT | 3.1.1 | IoT Communication |
| WebSocket | STOMP | - | Real-time Updates |
| Container | Docker | - | Application Packaging |
| Orchestration | Kubernetes | - | Production Deployment |
| CI/CD | Jenkins | - | Build Automation |

## Future Enhancements

### Planned Features
- Mobile application development
- Advanced analytics and ML integration
- Multi-tenant architecture
- API gateway implementation
- Service mesh (Istio)
- Advanced monitoring (Prometheus/Grafana)

### Scalability Improvements
- Database sharding
- CDN integration
- Global deployment
- Edge computing support

This architecture provides a robust, scalable foundation for IoT automation and device management, with clear separation of concerns and modern development practices.</content>
<parameter name="filePath">d:\Projects\Automata\ARCHITECTURE.md