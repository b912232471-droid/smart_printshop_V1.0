FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-noto-cjk fonts-wqy-microhei \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/printshop-0.0.1-SNAPSHOT.jar app.jar
ENV PRINTSHOP_SERVER_PORT=8081
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]