# Generic runtime image for the Spring Boot modules, built from the locally packaged jar
# (mvn -B package -DskipTests) so the system/E2E stack always runs the code under test.
FROM eclipse-temurin:17-jre
ARG MODULE
COPY ${MODULE}/target/${MODULE}-1.0-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
