FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/schedule-service-0.0.1-SNAPSHOT.jar /app/app.jar
ENV SCHEDULE_SERVER_PORT=8092
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
