# Use correct Java image
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy all files
COPY . .

# Build project
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Run app
CMD ["java", "-jar", "target/*.jar"]