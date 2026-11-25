# Builds one service module from this repository's source.
# Usage: docker build --build-arg SERVICE=order-service -t order-service .
ARG MAVEN_IMAGE=maven:3.10-eclipse-temurin-17-noble@sha256:49b578b852fd926e97d5b8579b2dc3efca64682640697ed01e89a3d3b114b096
ARG JRE_IMAGE=eclipse-temurin:17-jre-noble@sha256:ad806840ae514b83d3d8e3a00a5c1b7302fb0a6ae02bbbaf8a8bf1ffdf5367c6

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
# Run from an exploded classpath (as Jib did) so threads on the common
# ForkJoinPool, which use the system class loader, can load application classes.
RUN mkdir /workspace/exploded && cd /workspace/exploded && jar xf /workspace/app.jar \
    && main_class="$(sed -n 's/^Start-Class: *//p' META-INF/MANIFEST.MF | tr -d '\r')" \
    && test -n "${main_class}" \
    && printf -- '-cp /app/classes:/app/libs/*\n%s\n' "${main_class}" > /workspace/java.args

FROM ${JRE_IMAGE}
WORKDIR /app
COPY --from=build /workspace/exploded/BOOT-INF/lib/ /app/libs/
COPY --from=build /workspace/exploded/BOOT-INF/classes/ /app/classes/
COPY --from=build /workspace/java.args /app/java.args
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "@/app/java.args"]
