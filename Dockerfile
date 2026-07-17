# ---- Stage 1: Extract layers ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY target/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- Stage 2: Runtime image ----
FROM eclipse-temurin:21-jre-alpine
ENV TZ=Asia/Kolkata

RUN addgroup -S automata && adduser -S automata -G automata
WORKDIR /app

COPY --from=builder --chown=automata:automata /app/extracted/dependencies/ ./
COPY --from=builder --chown=automata:automata /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=automata:automata /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=automata:automata /app/extracted/application/ ./

USER automata
EXPOSE 8010

ENTRYPOINT ["java", \
  "-XX:+UseParallelGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]