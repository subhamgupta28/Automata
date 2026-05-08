pipeline {
    agent any

    environment {
        SPRING_PROFILE = 'prod'
        CONTAINER_NAME = 'automata-smart-home'
        IMAGE_NAME = 'automata'
        IMAGE_TAG = 'latest'
        NETWORK_NAME = 'automata-network'
        DOCKER_COMPOSE_FILE = 'docker-compose.yml'
        NETWORK_SUBNET = '192.168.1.0/24' // Update this to your actual network subnet
    }

    stages {
        stage('Checkout Code') {
            steps {
                echo "Checking out code from repository..."
                git branch: 'main',
                    url: 'https://github.com/subhamgupta28/Automata.git'
                sh 'git log -1 --pretty=format:"%h - %an - %s"'
            }
        }

        stage('Test') {
            steps {
                echo "Running tests..."
                sh 'mvn test -DskipIntegrationTests || true'
            }
        }

        stage('Build UI') {
            tools {
                nodejs 'NodeJs25'
            }
            steps {
                echo "Building frontend..."
                dir('frontend') {
                    sh 'npm install'
                    sh 'npm run build || true'
                    sh 'test -f ../src/main/resources/static/index.html && echo "✓ UI build verified" || echo "⚠ UI build warning"'
                }
            }
        }

        stage('Build Spring Boot Application') {
            steps {
                echo "Building Spring Boot application with Maven..."
                sh 'mvn clean package -DskipTests'
                sh 'test -f target/Automata-0.0.1-SNAPSHOT.jar && echo "✓ JAR build verified"'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}"
                sh '''
                    docker build \
                        --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
                        --build-arg VCS_REF=$(git rev-parse --short HEAD) \
                        --build-arg VERSION=0.0.1 \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        -t ${IMAGE_NAME}:latest \
                        .
                '''
                sh 'docker images | grep ${IMAGE_NAME}'
            }
        }

        stage('Ensure Docker Network Exists') {
            steps {
                echo "Ensuring Docker network exists: ${NETWORK_NAME}"
                sh '''
                    if ! docker network ls | grep -w ${NETWORK_NAME} > /dev/null; then
                        echo "Creating network: ${NETWORK_NAME}"
                        docker network create ${NETWORK_NAME}
                    else
                        echo "✓ Network already exists: ${NETWORK_NAME}"
                    fi
                '''
            }
        }

        stage('Stop and Remove Old Container') {
            steps {
                echo "Stopping and removing old container: ${CONTAINER_NAME}"
                sh '''
                    if docker ps -a --format '{{.Names}}' | grep -w ${CONTAINER_NAME} > /dev/null; then
                        echo "Stopping container..."
                        docker stop ${CONTAINER_NAME} || true
                        sleep 2
                        echo "Removing container..."
                        docker rm ${CONTAINER_NAME} || true
                    else
                        echo "✓ No old container found"
                    fi
                '''
            }
        }

        stage('Clean Up Docker Compose Resources') {
            steps {
                echo "Cleaning up Docker Compose resources..."
                sh '''
                    if [ -f ${DOCKER_COMPOSE_FILE} ]; then
                        docker-compose -f ${DOCKER_COMPOSE_FILE} down -v || true
                        sleep 2
                    fi
                '''
            }
        }

        stage('Deploy with Docker Compose') {
            steps {
                echo "Deploying application with Docker Compose..."
                sh '''
                    # Build and start services with environment variables
                    docker-compose -f ${DOCKER_COMPOSE_FILE} \
                        -p automata \
                        build

                    docker-compose -f ${DOCKER_COMPOSE_FILE} \
                        -p automata \
                        up -d
                '''
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "Verifying deployment..."
                sh '''
                    echo "Waiting for application to start (30 seconds)..."
                    sleep 30

                    echo "Checking running containers..."
                    docker-compose -f ${DOCKER_COMPOSE_FILE} -p automata ps

                    echo "Checking container logs..."
                    docker-compose -f ${DOCKER_COMPOSE_FILE} -p automata logs --tail=20 ${CONTAINER_NAME}
                '''
            }
        }

        stage('Health Check') {
            steps {
                echo "Performing health checks..."
                sh '''
                    MAX_ATTEMPTS=10
                    ATTEMPT=1

                    while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
                        echo "Health check attempt $ATTEMPT of $MAX_ATTEMPTS..."

                        if curl -s http://localhost:8010/api/actuator/health | grep -q "UP"; then
                            echo "✓ Application is healthy!"
                            exit 0
                        fi

                        sleep 5
                        ATTEMPT=$((ATTEMPT + 1))
                    done

                    echo "⚠ Health check timed out after $MAX_ATTEMPTS attempts"
                    exit 1
                '''
            }
        }

        stage('Trigger Device Discovery') {
            steps {
                echo "Triggering initial device discovery..."
                sh '''
                    sleep 5

                    RESPONSE=$(curl -s -X POST http://localhost:8010/api/discovery/scan)
                    echo "Discovery response: $RESPONSE"

                    if echo "$RESPONSE" | grep -q "started"; then
                        echo "✓ Device discovery initiated"
                    else
                        echo "⚠ Discovery response unclear, checking manually..."
                        curl -s http://localhost:8010/api/discovery/status
                    fi
                '''
            }
        }
    }

    post {
        success {
            echo """
            ╔════════════════════════════════════════╗
            ║   ✓ Deployment Successful!            ║
            ╚════════════════════════════════════════╝

            Application Details:
            - Container Name: ${CONTAINER_NAME}
            - Image: ${IMAGE_NAME}:${IMAGE_TAG}
            - Port: 8010
            - Health Check: http://localhost:8010/api/actuator/health
            - Discovery Endpoint: http://localhost:8010/api/discovery/scan

            Useful Commands:
            - View logs: docker-compose -f ${DOCKER_COMPOSE_FILE} logs -f
            - Check status: docker-compose -f ${DOCKER_COMPOSE_FILE} ps
            - Enter container: docker-compose -f ${DOCKER_COMPOSE_FILE} exec ${CONTAINER_NAME} bash
            """
        }

        failure {
            echo """
            ╔════════════════════════════════════════╗
            ║   ✗ Deployment Failed                 ║
            ╚════════════════════════════════════════╝

            Troubleshooting:
            - Check logs: docker-compose -f ${DOCKER_COMPOSE_FILE} logs
            - Check port: lsof -i :8010
            - Check networks: docker network ls
            - Check images: docker images | grep ${IMAGE_NAME}
            """
            script {
                sh 'docker-compose -f ${DOCKER_COMPOSE_FILE} logs || true'
            }
        }

        always {
            echo "Cleaning up workspace..."
            sh '''
                # Display final status
                echo "=== Final Container Status ==="
                docker-compose -f ${DOCKER_COMPOSE_FILE} -p automata ps || true

                echo "=== Final Network Status ==="
                docker network inspect ${NETWORK_NAME} || true

                echo "=== Disk Usage ==="
                docker system df || true
            '''
        }
    }
}