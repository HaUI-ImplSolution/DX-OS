# Two images are built from this file, one per runnable module:
#   docker build --target backend .   (default) the backend, dx-host.jar
#   docker build --target monitor .             the monitoring service, dx-monitor.jar
FROM eclipse-temurin:21-jdk-alpine AS base

WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml eclipse-java-google-style.xml ./
COPY dx-core-api/pom.xml dx-core-api/pom.xml
COPY dx-core/pom.xml dx-core/pom.xml
COPY app-farm/pom.xml app-farm/pom.xml
COPY app-lab/pom.xml app-lab/pom.xml
COPY dx-host/pom.xml dx-host/pom.xml
COPY dx-monitor/pom.xml dx-monitor/pom.xml
# Make sure to run with linux line endings
RUN sed -i 's/\r$//' mvnw
RUN chmod +x ./mvnw

FROM base AS build-host
COPY dx-core-api/src/ dx-core-api/src/
COPY dx-core/src/ dx-core/src/
COPY app-farm/src/ app-farm/src/
COPY app-lab/src/ app-lab/src/
COPY dx-host/src/ dx-host/src/
RUN ./mvnw clean package -DskipTests -pl dx-host -am

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
COPY --from=build-host /app/dx-host/target/dx-host.jar app.jar
# The service writes nothing outside /tmp, so it needs no privileges.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
