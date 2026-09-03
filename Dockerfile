# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup --system spring && adduser --system --ingroup spring spring

WORKDIR /app
USER spring:spring

COPY --from=build --chown=spring:spring \
  /workspace/target/mygamelist-api-0.0.1-SNAPSHOT.jar \
  /app/mygamelist-api.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/mygamelist-api.jar"]
