# ---- Stage 1: Extract layers from the pre-built jar ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY target/Automata-0.0.1-SNAPSHOT.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- Stage 2: Build a CDS archive using the extracted layers ----
FROM eclipse-temurin:21-jdk-alpine AS cds
WORKDIR /app
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./
RUN java -XX:ArchiveClassesAtExit=app.jsa \
         org.springframework.boot.loader.launch.JarLauncher \
         --spring.context.exit=onRefresh

# ---- Stage 3: Minimal runtime image ----
FROM eclipse-temurin:21-jre-alpine
ENV TZ=Asia/Kolkata

RUN addgroup -S automata && adduser -S automata -G automata
WORKDIR /app

COPY --from=cds --chown=automata:automata /app/ ./
COPY --from=cds --chown=automata:automata /app/app.jsa ./app.jsa

USER automata
EXPOSE 8010

ENTRYPOINT ["java", \
  "-XX:SharedArchiveFile=app.jsa", \
  "-XX:+UseParallelGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "org.springframework.boot.loader.launch.JarLauncher"]