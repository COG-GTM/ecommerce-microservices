# Builds one service module from this repository's source.
# Usage: docker build --build-arg SERVICE=order-service -t order-service .
ARG MAVEN_IMAGE=maven:3.10.2-eclipse-temurin-17-noble@sha256:92cc36d601d91e9bb0175d6292238aa632297d39317781d7fa178abbc175dc09
ARG JRE_IMAGE=eclipse-temurin:17.0.21.1_2-jre-noble@sha256:a9fc3bded7efe95fe0c06f3f5d7e5beea947925f729b71b61e39e0b9ddd4d7e3

FROM ${MAVEN_IMAGE} AS build
ARG SERVICE
RUN test -n "${SERVICE}" || (echo "SERVICE build arg is required" >&2 && exit 1)
WORKDIR /workspace
COPY pom.xml ./
COPY api-gateway/pom.xml api-gateway/
COPY discovery-server/pom.xml discovery-server/
COPY inventory-service/pom.xml inventory-service/
COPY notification-service/pom.xml notification-service/
COPY order-service/pom.xml order-service/
COPY product-service/pom.xml product-service/
COPY ${SERVICE}/src ${SERVICE}/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl "${SERVICE}" -am -DskipTests package \
    && cp "${SERVICE}"/target/"${SERVICE}"-*.jar /workspace/app.jar

FROM ${JRE_IMAGE}
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
