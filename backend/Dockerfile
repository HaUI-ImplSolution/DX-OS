# SPDX-License-Identifier: Apache-2.0
# Two images are built from this file, one per runnable module:
#   docker build --target backend .   (default) the backend, dx-engine.jar
#   docker build --target monitor .             the monitoring service, dx-monitor.jar
FROM eclipse-temurin:21-jdk-alpine AS base

WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml eclipse-java-google-style.xml ./
COPY dx-model/pom.xml dx-model/pom.xml
COPY dx-core/pom.xml dx-core/pom.xml
COPY dx-engine/pom.xml dx-engine/pom.xml
COPY dx-monitor/pom.xml dx-monitor/pom.xml
# Make sure to run with linux line endings
RUN sed -i 's/\r$//' mvnw
RUN chmod +x ./mvnw

FROM base AS build-engine
COPY dx-model/src/ dx-model/src/
COPY dx-core/src/ dx-core/src/
COPY dx-engine/src/ dx-engine/src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests -pl dx-engine -am

FROM base AS build-monitor
COPY dx-monitor/src/ dx-monitor/src/
RUN ./mvnw clean package -DskipTests -pl dx-monitor -am

FROM eclipse-temurin:21-jre-alpine AS monitor
WORKDIR /app
ENV TZ=Asia/Ho_Chi_Minh
COPY --from=build-monitor /app/dx-monitor/target/dx-monitor.jar app.jar
# The service writes nothing outside /tmp, so it needs no privileges.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
USER app
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]

# Last stage, so it is the default target.
FROM eclipse-temurin:21-jre-alpine AS backend
WORKDIR /app
ENV TZ=Asia/Ho_Chi_Minh
COPY --from=build-engine /app/dx-engine/target/dx-engine.jar app.jar
# pgschema compares the tables with what a model declares and changes them.
COPY --from=pgplex/pgschema:1.13.4@sha256:4e77c22878120b38f8548d6ea62b3b1564438a5fbe84fcde3019684a638f60b7 /usr/local/bin/pgschema /usr/local/bin/pgschema
# The service writes nothing outside /tmp, so it needs no privileges.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
