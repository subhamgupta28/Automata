# **Project Documentation**

## **Project Name**

---

## **Overview**
This project is a modern web application built with a **Java (Jakarta EE)** backend and a feature-rich **React** frontend interface. Designed for seamless interaction and management, the project integrates powerful frameworks, real-time messaging, efficient data processing, and interactive UI elements.

### **Key Technologies**
- **Java (Jakarta EE)** with **Jakarta Imports**.
- **Spring Framework**: Includes Spring Data for MongoDB and Spring MVC.
- **React** for a dynamic, component-based frontend.
- **Material-UI** for comprehensive, responsive UI components.
- **MongoDB** as the NoSQL database.
- **WebSocket (via SockJS)** for real-time communication.

### **Purpose**
The purpose of this system is to provide an efficient tool for device management, automated action orchestration, and real-time data visualization.

---

## **Key Features**

1. **Device Management Dashboard**
    - Manage and monitor your devices.
    - Add, edit, or remove devices.
    - View useful device details, statuses, and operational logs.

2. **Action Automation Board**
    - Create and manage custom device-specific actions.
    - Automate the execution of routines and tasks.
    - Track and monitor status of all action workflows.

3. **Real-Time Data Updates**
    - **WebSocket**: Ensure live updates on device statuses, logs, and actions.
    - Use **StompJS** and **SockJS** to establish WebSocket communication seamlessly.
    - Keep the user interface synced with real-time backend data.

4. **Responsive UI**
    - Built with **Material-UI** for flexible compatibility across devices.
    - Optimized for both desktop and mobile screens.

5. **Interactive Data Charts**
    - Powered by **Chart.js** and **react-chartjs-2** for data visualization.
    - Display trends and analytics for devices or actions in visually appealing charts.

6. **Mapping and Geolocation**
    - Fully integrated using **Leaflet** and **react-leaflet.**
    - Visualize device locations, coverage areas, and geospatial data on a dynamic map.

---

## **Technical Stack**

### **Backend**
- **Java SDK 21**: Reliable and robust base for application logic.
- **Spring Boot** Framework:
    - **Spring MVC** for RESTful web services.
    - **Spring Data MongoDB** for efficient NoSQL database interactions.
- **MongoDB**: Robust and scalable NoSQL database for managing fast-changing data.
- **Jackson** (built into Spring): Used for JSON serialization/deserialization.

### **Frontend**
- **React (18.3.1)**: Provides a dynamic, componentized frontend structure.
    - Works alongside `react-dom` and React-specific plugins like `@emotion/react` for styling.
- **Material-UI (v6+)**: Entire frontend built with rich MUI components, supporting responsive designs.
- **Axios**: Simplifies HTTP requests and handles API calls efficiently.
- **Chart.js & react-chartjs-2**: Implements charts via reactive components.
- **Leaflet** & **react-leaflet**: Incorporates live, interactive maps into the application.

### **Real-Time Communication**
- **SockJS** + **StompJS**: WebSocket tools to handle live message delivery between backend and frontend.

### **Build Tools**
- **Maven**: Ties backend dependencies and builds a deployable Java application.
- **Vite (5.4.1)**: Vite’s fast module bundler ensures short build cycles and optimized delivery for React.
- **npm** (Node Package Manager): Manages JavaScript dependencies and packages like `eslint`, `vite`, etc.

---
## **Run & Deployment Guide**

### **Prerequisites**
1. **Backend**:
    - Java 21 installed.
    - MongoDB setup and running (Update `application.properties` with database connection string).
    - Maven installed.

2. **Frontend**:
    - Node.js and npm must be installed.

---

### **Running the Application Locally**

#### Backend
1. Navigate to the backend directory.
2. Run:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
3. Access the server at `http://localhost:8080`.

#### Frontend
1. Navigate to the frontend directory.
2. Install dependencies:
   ```bash
   npm install
   ```
3. Run the development server:
   ```bash
   npm run dev
   ```
4. Open the React app at `http://localhost:5173`.

---

### **Deployment**
1. Build a production-ready frontend:
   ```bash
   npm run build
   ```
   This generates optimized static files inside a `build/` directory.

2. Package backend and combine with the frontend static files.
3. Deploy the application to any Java-supported hosting provider or containerized platform like Docker.

---

## **Real-Time WebSocket Configuration**
The WebSocket connections can handle bidirectional messages from backend to frontend. Backend is configured using Spring WebSocket; SockJS is used as fallbacks. Messages are processed in `stomp` format.

---

## **Testing and Validation**

- **Frontend**:
    - Use **Jest** and **React Testing Library** for component/unit tests.
    - Add `eslint` integration for static code analysis.

- **Backend**:
    - Use **JUnit** for unit tests.
    - Use **Mockito** for mocking services and repositories.

Integration tests are recommended using tools like `Postman` or automated API test frameworks.

---

## **Future Enhancements**
- Expand device management capabilities to include predictive maintenance.
- Add user role-based authentication (e.g., Admin/Guest users).
- Improve chart visualization (add AI/ML predictions in graphs).
- Enhance map functionality to calculate geospatial routes.

---
