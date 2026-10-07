FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean verify
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S panel && adduser -S panel -G panel
WORKDIR /app
COPY --from=build /app/target/appcontrol-panel-1.0.0.jar app.jar
USER panel
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
