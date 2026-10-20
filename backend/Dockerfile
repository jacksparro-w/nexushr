FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
RUN mvn -B -f backend/pom.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nexushr && adduser -S -G nexushr -u 10001 nexushr
WORKDIR /app
COPY --from=build --chown=nexushr:nexushr /workspace/backend/target/nexushr-backend-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8090 8091
HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8091/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
