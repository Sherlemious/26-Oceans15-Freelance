FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build

COPY api-gateway/pom.xml api-gateway/pom.xml
COPY api-gateway/src api-gateway/src
RUN mvn -f api-gateway/pom.xml clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=builder /build/api-gateway/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
