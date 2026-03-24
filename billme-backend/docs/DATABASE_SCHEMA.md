# BillMe Database Schema

## Entity Relationship Diagram (ERD)

```mermaid
erDiagram
    users ||--o{ merchant_profiles : "One-to-One"
    users ||--o{ customer_profiles : "One-to-One"
    users ||--o{ products : "One-to-Many"
    merchant_profiles ||--o{ invoices : "Issues"
    customer_profiles ||--o{ invoices : "Receives"
    invoices ||--|{ invoice_items : "Contains"
    products ||--o{ invoice_items : "Referenced By"

    users {
        bigint id PK
        varchar email
        varchar name
        varchar password
        enum role "MERCHANT, CUSTOMER, ADMIN"
        datetime created_at
    }

    merchant_profiles {
        bigint id PK
        bigint user_id FK
        varchar business_name
        varchar owner_name
        varchar phone
        varchar address
        varchar upi_id
        boolean gst_registered
        varchar gstin
        varchar bank_name
        varchar account_holder_name
        varchar account_number
        varchar ifsc_code
    }

    customer_profiles {
        bigint id PK
        bigint user_id FK
        date dob
        int age
        varchar address
        text face_embeddings
    }

    products {
        bigint id PK
        bigint merchant_id FK
        varchar name
        varchar description
        decimal price
        decimal gst_rate
        varchar sku
        datetime created_at
    }

    invoices {
        bigint id PK
        varchar invoice_number
        bigint merchant_id FK
        bigint customer_id FK
        decimal subtotal
        decimal total_tax
        decimal platform_fee
        decimal total_amount
        enum status "UNPAID, PROCESSING, PAID, FAILED"
        enum payment_method "RAZORPAY, FACEPAY, NONE"
        datetime issued_at
        datetime due_date
        datetime paid_at
    }

    invoice_items {
        bigint id PK
        bigint invoice_id FK
        bigint product_id FK
        varchar product_name
        int quantity
        decimal unit_price
        decimal tax_amount
        decimal line_total
    }
```

## Platform Fee Logic Layer
- **Platform Fee Tracking**: Each `invoice` record natively tracks its own `platform_fee`. At the time of transaction execution, a 1.5% fee is calculated from `total_amount` (or `subtotal` depending on phase configs) and stored immutably against the transaction.
- **Wallet Balances**: The `BalanceSheetService` dynamically sums `(total_amount - platform_fee)` for all `status = PAID` invoices belonging to a specific `merchant_id`. Instead of a rigid `wallet_balance` column that can drift out of sync, it calculates it accurately from raw transactional history.
