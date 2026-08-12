# --- Build stage ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copy gradle wrapper and build files first for better layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
# Warm the dependency cache (safe to fail if this project doesn't have a "dependencies" task alias)
RUN ./gradlew dependencies --no-daemon || true

# Now copy the rest of the source and build
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# --- Run stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create a non-root user to run the app
RUN useradd --create-home --shell /bin/bash appuser
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
