FROM openjdk:21-slim
WORKDIR /app
COPY target/jettraDBVM-1.0-SNAPSHOT.jar app.jar
COPY config.json config.json
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
