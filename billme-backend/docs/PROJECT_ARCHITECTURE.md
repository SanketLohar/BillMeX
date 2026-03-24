# BillMe Project Architecture

## Overview
BillMe is a modern fintech billing platform that allows merchants to register, manage products, and generate invoices with ease. Customers can securely pay their invoices using standard payment gateways (Razorpay) or utilizing advanced Biometric Facial Recognition (FacePay). The system is built with a strictly typed Java backend and a Vanilla JS modular frontend architecture, ensuring high performance, stability, and premium SaaS-level aesthetics.

## Technology Stack

### Backend
- **Core Framework**: Spring Boot 3.x
- **Language**: Java 17
- **Database**: MySQL 8.0
- **ORM / Data Access**: Spring Data JPA & Hibernate
- **Security**: Spring Security & JWT (JSON Web Tokens)
- **Email Service**: JavaMailSender (Mailtrap for testing)
- **Report Generation**: Apache POI (Excel `.xlsx` export)

### Frontend
- **Languages**: HTML5, CSS3, Vanilla JavaScript (ESM)
- **Architecture**: Modular component-level architecture with specific service abstractions
- **Styling**: Custom CSS Variables, Base Components, Glassmorphism UI
- **Libraries**:
  - `Chart.js` (for Analytics Dashboards)
  - `face-api.js` (for FacePay biometric features)
  - `Razorpay JS SDK` (for standard payment checkout)

## High-Level Architecture

The system operates on an API-driven Client-Server model. The frontend serves solely as an implementation of the UI, while all substantial business logic, validation, tax calculations (GST), and pricing logic resides securely within the Spring Boot backend.

```text
[ Frontend Browser ]
    |      |
    |      v (REST APIs / JSON)
    |      |
    |   [ Spring Security Filter Chain (JWT Auth) ]
    |      |
    |   [ Spring Boot Controllers ] -> Auth, Merchant, Customer, Invoice, Report
    |      |
    |   [ Service Layer ] -> Business Logic & Calculations
    |      |
    V   [ Repository Layer ] -> Spring Data JPA
[ End Users ]
```

## Core Modules

### 1. Authentication & Authorization (`com.billme.auth`, `com.billme.security`)
- Role-based Access Control (`MERCHANT`, `CUSTOMER`, `ADMIN`).
- Stateless authentication using JWT tokens.
- Secure password hashing using BCrypt.

### 2. User Profiles (`com.billme.customer`, `com.billme.merchant`)
- Strict partition between Merchant profile data (GST, Bank info, UPI) and Customer profile data (Age, DoB, Face embeddings).

### 3. Products & Inventory (`com.billme.product`)
- Merchants can create and manage products, defining their base price and applicable GST rates.

### 4. Invoicing System (`com.billme.invoice`)
- Handles Point-of-Sale (POS) style invoice generation.
- Dynamic GST deduction based on merchant registration status.
- State machines for Invoices (`UNPAID` -> `PROCESSING` -> `PAID`).
- Automatic HTML email dispatching.

### 5. Payment Gateway & FacePay (`com.billme.payment`)
- Interface with Razorpay for handling UPI/Card checkout.
- Biometric verification handling for Customer FacePay.

### 6. Accounting & Reports (`com.billme.report`)
- Aggregates system metrics into Balance Sheets.
- Generates Excel exports via Apache POI.

## Deployment Strategy
BillMe is designed as a standalone `.jar` monolith and static content hostable via NGINX or injected directly into the Spring Boot `/static` resources. Standard deployment involves Dockerizing the Spring application and the MySQL Database together.
