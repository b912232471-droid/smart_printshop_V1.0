FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/printshop-gateway-0.0.1-SNAPSHOT.jar app.jar
ENV GATEWAY_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
