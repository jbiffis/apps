# --- Build stage -----------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Cache dependencies first
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Build
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package \
    && mv target/*.jar target/app.jar

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Non-root user
RUN groupadd -r tracker && useradd -r -g tracker tracker
USER tracker

COPY --from=build /app/target/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
