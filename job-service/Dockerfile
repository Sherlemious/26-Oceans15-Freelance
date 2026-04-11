# Build stage
FROM eclipse-temurin:25.0.2_10-jdk AS builder

WORKDIR /app

COPY pom.xml .

COPY src/ src/

RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests

FROM eclipse-temurin:25.0.2_10-jdk

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]