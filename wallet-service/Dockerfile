FROM eclipse-temurin:25.0.2_10-jdk
WORKDIR /app
COPY target/wallet-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]