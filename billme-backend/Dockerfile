FROM eclipse-temurin:17-jdk

# Set working dir
WORKDIR /app

# Copy backend project ONLY
COPY billme-backend/ .

# Debug (optional but useful)
RUN ls -la

# Make mvnw executable
RUN chmod +x ./mvnw

# Build project
RUN ./mvnw clean package -DskipTests

# Run jar
CMD ["java", "-jar", "target/*.jar"]