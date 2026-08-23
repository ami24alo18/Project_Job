FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S jobagent && adduser -S jobagent -G jobagent
WORKDIR /app
COPY --from=build /workspace/target/job-agent-backend-*.jar app.jar
USER jobagent
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
