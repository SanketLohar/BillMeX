# BillMe System Flows

This document details the critical system flows within the BillMe platform using Mermaid diagrams.

## 1. Authentication & Registration Flow

```mermaid
sequenceDiagram
    participant User
    participant Frontend
    participant AuthController
    participant Database
    
    User->>Frontend: Fills Registration Form
    Frontend->>AuthController: POST /auth/register/{role}
    AuthController->>Database: Verify Email & Save User
    Database-->>AuthController: OK
    AuthController-->>Frontend: JWT Token & User Details
    Frontend->>Frontend: Save Token to LocalStorage
    Frontend-->>User: Redirect to Dashboard
```

## 2. Point of Sale (POS) & Invoice Generation Flow

```mermaid
sequenceDiagram
    participant Merchant
    participant Frontend
    participant InvoiceService
    participant EmailService
    participant Database
    
    Merchant->>Frontend: Selects Customer & Products
    Frontend->>InvoiceService: POST /merchant/invoices (Payload: Items)
    InvoiceService->>Database: Fetch Product Prices & GST Rates
    InvoiceService->>InvoiceService: Calculate Total & Tax
    InvoiceService->>Database: Save Invoice (Status: UNPAID)
    InvoiceService->>EmailService: triggerAsyncEmail(Customer)
    EmailService-->>Customer: Payment Link Email
    InvoiceService-->>Frontend: Invoice Created
    Frontend-->>Merchant: Success UI
```

## 3. Standard Payment Flow (Razorpay)

```mermaid
sequenceDiagram
    participant Customer
    participant Frontend
    participant Razorpay
    participant PaymentController
    participant Database    

    Customer->>Frontend: Clicks "Pay with Razorpay"
    Frontend->>PaymentController: POST /api/payments/create-order/{id}
    PaymentController->>Razorpay: Generate Razorpay Order ID
    Razorpay-->>PaymentController: OrderID
    PaymentController-->>Frontend: OrderID, Amount, Key
    Frontend->>Razorpay: Initialize Checkout (rzp.open)
    Customer->>Razorpay: Completes Payment
    Razorpay-->>Frontend: success (payment_id, signature)
    Frontend->>PaymentController: POST /api/payments/verify
    PaymentController->>PaymentController: Validate HMAC Signature
    PaymentController->>Database: Mark Invoice 'PAID'
    PaymentController-->>Frontend: Payment Success
    Frontend-->>Customer: Thank You Page
```

## 4. FacePay Biometric Payment Flow

```mermaid
sequenceDiagram
    participant Customer
    participant WebCam
    participant Frontend
    participant PaymentController
    participant Database

    Customer->>Frontend: Clicks "Pay with FacePay"
    Frontend->>WebCam: Request Camera Access
    WebCam-->>Frontend: Video Stream
    Frontend->>Frontend: Run face-api.js -> Extract Embeddings [Float Array]
    Frontend->>PaymentController: POST /customer/invoices/{id}/pay/face
    PaymentController->>Database: Fetch User Stored Embeddings
    PaymentController->>PaymentController: Calculate Euclidean Distance
    alt Match < Threshold (0.6)
        PaymentController->>PaymentController: Process Payment via Wallet/Saved Card
        PaymentController->>Database: Mark Invoice 'PAID'
        PaymentController-->>Frontend: Face Match Verified
        Frontend-->>Customer: Success Confetti
    else Match >= Threshold
        PaymentController-->>Frontend: 400 Face Not Matched
        Frontend-->>Customer: Auth Failed
    end
```

## 5. Wallet Settlement & Reporting Flow

```mermaid
sequenceDiagram
    participant Merchant
    participant Frontend
    participant ReportController
    participant BalanceSheetService
    participant Database
    
    Merchant->>Frontend: View Dashboard / Reports
    Frontend->>ReportController: GET /api/merchant/reports/balance-sheet
    ReportController->>BalanceSheetService: generateSheet()
    BalanceSheetService->>Database: Fetch All PAST_PAID Invoices
    BalanceSheetService->>BalanceSheetService: Calculate Platform Fee (1.5%)
    BalanceSheetService->>BalanceSheetService: Sum Wallet Balance = Revenue - Fees
    BalanceSheetService-->>Frontend: Return Aggregated Totals
    Merchant->>Frontend: Clicks "Withdraw Funds" (Future)
    Frontend->>ReportController: POST /api/merchant/withdraw
    ReportController->>Database: Trigger Payout against UI/UPI
```
