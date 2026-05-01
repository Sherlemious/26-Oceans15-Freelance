FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build

COPY security-common/pom.xml security-common/pom.xml
COPY security-common/src security-common/src
RUN mvn -f security-common/pom.xml clean install -DskipTests --no-transfer-progress

COPY wallet-service/pom.xml wallet-service/pom.xml
COPY wallet-service/src wallet-service/src
RUN mvn -f wallet-service/pom.xml clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=builder /build/wallet-service/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]