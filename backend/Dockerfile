FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml eclipse-java-google-style.xml ./
COPY dx-core-api/pom.xml dx-core-api/pom.xml
COPY dx-core/pom.xml dx-core/pom.xml
COPY app-farm/pom.xml app-farm/pom.xml
COPY dx-host/pom.xml dx-host/pom.xml
# Make sure to run with linux line endings
RUN sed -i 's/\r$//' mvnw
RUN chmod +x ./mvnw

COPY dx-core-api/src/ dx-core-api/src/
COPY dx-core/src/ dx-core/src/
COPY app-farm/src/ app-farm/src/
COPY dx-host/src/ dx-host/src/
RUN ./mvnw clean package -DskipTests -pl dx-host -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV TZ=Asia/Ho_Chi_Minh
COPY --from=builder /app/dx-host/target/dx-host.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
