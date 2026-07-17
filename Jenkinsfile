pipeline {
    agent none

    environment {
        CONTAINER_NAME   = 'automata'
        NETWORK_NAME     = 'bridge'
        SECOND_NETWORK   = 'automata-net'
        RADXA_NETWORK    = 'homelab'
    }

    stages {

        // ─────────────────────────────────────────────
        // STAGE 1: Checkout on RPi
        // ─────────────────────────────────────────────
        stage('Checkout Code') {
            agent { label 'rpi' }
            steps {
                git 'https://github.com/subhamgupta28/Automata.git'
                stash name: 'source', includes: '**/*', excludes: '.git/**'
            }
        }

        // ─────────────────────────────────────────────
        // STAGE 2: Resolve Version
        // ─────────────────────────────────────────────
        stage('Resolve Version') {
            agent { label 'rpi' }
            steps {
                unstash 'source'
                script {
                    def mvnVersion = sh(
                        script: "grep -m1 '<version>' pom.xml | sed 's/.*<version>\\(.*\\)<\\/version>.*/\\1/' | tr -d '[:space:]'",
                        returnStdout: true
                    ).trim()

                    def shortSha = sh(
                        script: "git rev-parse --short HEAD",
                        returnStdout: true
                    ).trim()

                    def branch = env.GIT_BRANCH?.replaceAll('origin/', '')?.replaceAll('[^a-zA-Z0-9_.-]', '-') ?: 'unknown'

                    // e.g. automata:1.0.0-main-ab3f91c
                    env.IMAGE_TAG = "${mvnVersion}-${branch}-${shortSha}"

                    echo "Image tag resolved: ${env.IMAGE_TAG}"
                }
            }
        }

        // ─────────────────────────────────────────────
        // STAGE 3: Build React UI on RPi
        // ─────────────────────────────────────────────
        stage('Build UI') {
            agent { label 'rpi' }
            tools { nodejs 'NodeJs25' }
            steps {
                unstash 'source'
                dir('frontend') {
                    sh 'npm install'
                    sh 'npm run build || true'
                    sh 'test -f ../src/main/resources/static/index.html && echo "UI build verified"'
                }
                stash name: 'source-with-ui', includes: '**/*', excludes: '.git/**'
            }
        }

        // ─────────────────────────────────────────────
        // STAGE 4: Build JAR on RPi
        // ─────────────────────────────────────────────
        stage('Build JAR') {
            agent { label 'rpi' }
            steps {
                unstash 'source-with-ui'
                sh 'mvn clean package -DskipTests'
                sh 'cp target/*.jar target/app.jar'
                stash name: 'build-output', includes: 'target/*.jar, Dockerfile'
            }
        }

        // ─────────────────────────────────────────────
        // STAGE 5: Parallel Deploy
        // ─────────────────────────────────────────────
        stage('Deploy') {
            parallel {

                // ── RPi Deploy ──────────────────────
                stage('Deploy → RPi') {
                    agent { label 'rpi' }
                    environment {
                        SPRING_PROFILE = 'prod'
                        FULL_IMAGE     = "automata:${IMAGE_TAG}-rpi"
                    }
                    steps {
                        unstash 'build-output'
                        sh 'ls -la target/'
                        sh 'docker build -t ${FULL_IMAGE} .'

                        sh '''
                            docker network ls | grep -w ${NETWORK_NAME} \
                                || docker network create ${NETWORK_NAME}
                            docker network ls | grep -w ${SECOND_NETWORK} \
                                || docker network create ${SECOND_NETWORK}

                            docker ps   -q -f name=${CONTAINER_NAME} | xargs -r docker stop
                            docker ps -a -q -f name=${CONTAINER_NAME} | xargs -r docker rm
                        '''

                        withCredentials([
                            string(credentialsId: 'spotify-client-id',    variable: 'SPOTIFY_CLIENT_ID'),
                            string(credentialsId: 'spotify-client-secret', variable: 'SPOTIFY_CLIENT_SECRET')
                        ]) {
                            sh '''
                                docker run -d --name ${CONTAINER_NAME} \
                                    --restart unless-stopped \
                                    --network ${NETWORK_NAME} \
                                    --add-host=host.docker.internal:host-gateway \
                                    --health-cmd="wget -qO- http://localhost:8010/actuator/health || exit 1" \
                                    --health-interval=30s --health-timeout=5s --health-retries=3 \
                                    -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILE} \
                                    -e SPOTIFY_CLIENT_ID="${SPOTIFY_CLIENT_ID}" \
                                    -e SPOTIFY_CLIENT_SECRET="${SPOTIFY_CLIENT_SECRET}" \
                                    -l automata.version="${IMAGE_TAG}" \
                                    -p 8010:8010 \
                                    ${FULL_IMAGE}
                            '''
                        }

                        sh 'docker network connect ${SECOND_NETWORK} ${CONTAINER_NAME}'
                    }
                }

                // ── Radxa Deploy ────────────────────
                stage('Deploy → Radxa') {
                    agent { label 'radxa' }
                    environment {
                        SPRING_PROFILE = 'radxa'
                        FULL_IMAGE     = "automata:${IMAGE_TAG}-radxa"
                    }
                    steps {
                        unstash 'build-output'
                        sh 'ls -la target/'
                        sh 'docker build -t ${FULL_IMAGE} .'

                        sh '''
                            docker network ls | grep -w ${SECOND_NETWORK} \
                                || docker network create ${SECOND_NETWORK}

                            docker ps   -q -f name=${CONTAINER_NAME} | xargs -r docker stop
                            docker ps -a -q -f name=${CONTAINER_NAME} | xargs -r docker rm
                        '''

                        withCredentials([
                            string(credentialsId: 'spotify-client-id',    variable: 'SPOTIFY_CLIENT_ID'),
                            string(credentialsId: 'spotify-client-secret', variable: 'SPOTIFY_CLIENT_SECRET')
                        ]) {
                            sh '''
                                docker run -d --name ${CONTAINER_NAME} \
                                    --restart unless-stopped \
                                    --network ${SECOND_NETWORK} \
                                    --add-host=host.docker.internal:host-gateway \
                                    --health-cmd="wget -qO- http://localhost:8010/actuator/health || exit 1" \
                                    --health-interval=30s --health-timeout=5s --health-retries=3 \
                                    -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILE} \
                                    -e SPOTIFY_CLIENT_ID="${SPOTIFY_CLIENT_ID}" \
                                    -e SPOTIFY_CLIENT_SECRET="${SPOTIFY_CLIENT_SECRET}" \
                                    -l automata.version="${IMAGE_TAG}" \
                                    -p 8010:8010 \
                                    ${FULL_IMAGE}
                            '''
                        }

                        sh 'docker network connect ${RADXA_NETWORK} ${CONTAINER_NAME}'
                    }
                }

            }
        }
    }

    post {
        success {
            echo "✅ Both RPi and Radxa deployed successfully! [${IMAGE_TAG}]"
        }
        failure {
            echo '❌ Deployment failed — check stage logs above.'
        }
        unstable {
            echo '⚠️ Build unstable — UI build may have warnings.'
        }
    }
}