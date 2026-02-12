# Build Stage
FROM gradle:jdk17 AS build

WORKDIR /app

# Copy gradle files
COPY build.gradle settings.gradle* gradlew ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Ensure gradlew has execution permissions
RUN chmod +x gradlew

# Build the JAR
RUN ./gradlew bootJar --no-daemon

# Production Stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8083

# Start command
ENTRYPOINT ["java", "-jar", "app.jar"]
