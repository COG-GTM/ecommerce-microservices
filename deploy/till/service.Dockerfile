# Runtime image for a Spring Boot module built from source on the host (scripts/till/up.sh runs `mvn package` first).
FROM eclipse-temurin:17-jre
ARG MODULE
WORKDIR /app
COPY deploy/till/healthcheck.sh /usr/local/bin/healthcheck
COPY ${MODULE}/target/${MODULE}-1.0-SNAPSHOT.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
