# BillMe Deployment Guide

## Prerequisites

- **Java 17+** (Required for Spring Boot 3.x)
- **MySQL 8.0+**
- **Node.js** (Optional: for running a local static file server like `serve` for the frontend)
- **Razorpay Keys** (Test or Live)

## Environment Setup

### 1. Database
Create a MySQL database named `billme`.
```sql
CREATE DATABASE billme;
CREATE USER 'billme_user'@'localhost' IDENTIFIED BY 'billme_password';
GRANT ALL PRIVILEGES ON billme.* TO 'billme_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Backend Environment Variables
Update the `application.properties` or provide environment variables at runtime.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/billme
spring.datasource.username=billme_user
spring.datasource.password=billme_password

jwt.secret=billme_super_secret_key_change_in_production_billme_super_secret_key
razorpay.key.id=rzp_test_YOUR_KEY_HERE
razorpay.key.secret=YOUR_SECRET_HERE
```

## Build and Run

### 1. Backend (Spring Boot)
Navigate to the root backend directory:
```bash
# Package the application
./mvnw clean package -DskipTests

# Run the jar file
java -jar target/billme-backend-0.0.1-SNAPSHOT.jar
```
The server will start on `http://localhost:8080`.

### 2. Frontend (Static HTML/JS)
Navigate to the `frontend` directory. The frontend is built using standard Vanilla JS and can be hosted on any static file server like NGINX, Apache, or a simple Node server.

```bash
# Using Node 'serve'
npx serve . -p 3000

# Using Python
python3 -m http.server 3000
```
Visit `http://localhost:3000` to access the application.

## Production Considerations
- **CORS**: Ensure `WebMvcConfig.java` allows exactly the origin of your production frontend (e.g. `https://app.billme.com`).
- **SSL/TLS**: Both the NGINX frontend host and the Spring Boot API should be served over HTTPS.
- **Database**: Disable `spring.jpa.hibernate.ddl-auto=update` in production. Use a tool like Flyway or Liquibase for schema migrations.
- **Microservices Setup**: The frontend files can be injected into the Spring Boot `/static` folder if a single `.jar` deployment is required. However, isolating the frontend on a CDN (Vercel, Netlify, CloudFront) while running the Java API on AWS ECS/EKS is highly advised for scalability.
