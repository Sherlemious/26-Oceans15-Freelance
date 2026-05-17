FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build

COPY security-common/pom.xml security-common/pom.xml
COPY security-common/src security-common/src
RUN mvn -f security-common/pom.xml clean install -DskipTests --no-transfer-progress

COPY event-common/pom.xml event-common/pom.xml
COPY event-common/src event-common/src
RUN mvn -f event-common/pom.xml clean install -DskipTests --no-transfer-progress

COPY contracts/pom.xml contracts/pom.xml
COPY contracts/src contracts/src
RUN mvn -f contracts/pom.xml clean install -DskipTests --no-transfer-progress

COPY proposal-service/pom.xml proposal-service/pom.xml
COPY proposal-service/src proposal-service/src
RUN mvn -f proposal-service/pom.xml clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=builder /build/proposal-service/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]