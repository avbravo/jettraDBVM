FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/jettra-server/target/jettraDBVM.jar /app/
COPY --from=build /app/jettra-shell/target/jettraDBVMShell.jar /app/

# Create data directory
RUN mkdir -p /app/data

EXPOSE 8080
EXPOSE 9000

CMD ["java", "-jar", "jettraDBVM.jar"]
