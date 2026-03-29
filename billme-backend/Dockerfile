FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy only backend folder
COPY billme-backend /app

# Go inside backend
WORKDIR /app

# Build project
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Run app
CMD ["java", "-jar", "target/*.jar"]