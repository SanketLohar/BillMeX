# BillMe API Documentation

## Base URL
`http://localhost:8080` (Development)

## Authentication Routes

| Endpoint | Method | Role | Description |
|:---|:---:|:---:|:---|
| `/auth/register/customer` | `POST` | Public | Registers a new Customer. |
| `/auth/register/merchant` | `POST` | Public | Registers a new Merchant. |
| `/auth/login` | `POST` | Public | Login for both roles, returns JWT (`token`). |
| `/auth/refresh` | `POST` | Any | Refreshes an expired JWT securely. |

## Merchant Routes (Requires `Bearer ...` Token, Role: `MERCHANT`)

| Endpoint | Method | Description |
|:---|:---:|:---|
| `/api/merchant/profile` | `GET` | Fetches the complete merchant profile, including GST config and bank data. |
| `/api/merchant/profile` | `PUT` | Updates the merchant profile. |
| `/api/merchant/products` | `GET` | Fetches all products owned by the merchant. |
| `/api/merchant/products` | `POST` | Creates a new product. |
| `/merchant/invoices` | `POST` | Creates a new invoice and triggers email notification. |
| `/api/merchant/invoices` | `GET` | Fetches all POS invoices created by the merchant. |
| `/customer/email/{email}` | `GET` | Searches for a customer by exact email for POS invoice association. |
| `/api/merchant/reports/balance-sheet` | `GET` | Aggregated metrics, wallet balance, totals. |
| `/api/merchant/reports/transactions` | `GET` | Array of recent transactions for charts/tables. |
| `/api/merchant/reports/export` | `GET` | Returns binary `.xlsx` file of all transactions. |

## Customer Routes (Requires Token, Role: `CUSTOMER`)

| Endpoint | Method | Description |
|:---|:---:|:---|
| `/api/customer/invoices` | `GET` | Fetch all invoices billed to this customer ID. |
| `/customer/invoices/{id}/pay/face` | `POST` | Triggers FacePay matching against `faceEmbeddings` array in payload. Mark PAId if match < 0.6. |

## Shared / Public Routes

| Endpoint | Method | Description |
|:---|:---:|:---|
| `/api/payments/create-order/{invoiceId}` | `POST` | Generates a Razorpay Order ID for standard checkout. |
| `/api/payments/verify` | `POST` | Verifies Razorpay Signature and marks invoice PAID. |
| `/public/invoices/{invoiceNumber}` | `GET` | Fetches a public read-only invoice via shared token link. |

## Admin Routes (Requires Token, Role: `ADMIN`)

| Endpoint | Method | Description |
|:---|:---:|:---|
| `/api/admin/summary` | `GET` | Fetches global platform revenue, metrics, and fees. |

---

## Example Payloads

### POST `/merchant/invoices`
```json
{
  "customerId": 1,
  "items": [
    {
      "productId": 2,
      "quantity": 1
    }
  ]
}
```

### POST `/customer/invoices/{id}/pay/face`
```json
{
  "faceEmbeddings": [0.1241, 0.4124, -0.9991, ...]
}
```
