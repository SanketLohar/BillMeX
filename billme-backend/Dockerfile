FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy current folder (IMPORTANT)
COPY . .

# Debug (keep this for now)
RUN ls -la

# Make mvnw executable
RUN chmod +x mvnw

# Build project
RUN ./mvnw clean package -DskipTests

# Run app
CMD ["java", "-jar", "target/billme-backend-0.0.1-SNAPSHOT.jar"]