# Order Service

Spring Boot order service with integrated frontend/backend flow for **Milestone 25% — Fondasi + Skeleton Berjalan**.  
Service ini menangani pembuatan order sederhana, menampilkan daftar order, menyediakan health check, dan menyiapkan integrasi lanjutan dengan modul lain seperti Auth, Inventory, Wallet, dan Voucher.

## Repository Links per Service

- **Frontend Order:** same repository (`frontend/`)
- **Backend Order:** same repository (`backend/`)
- **Database:** configured in backend environment for local/runtime usage
- **Integrated Frontend (gabungan tim):** ganti bagian ini dengan link repo frontend tim jika diperlukan

Jika tim menggunakan repository terpisah untuk frontend gabungan, tambahkan link repo tersebut di bagian ini.

Contoh format:
- **Order Repository:** `https://github.com/advprog-2026-A04-project/Order`
- **Frontend Integration Repository:** `https://github.com/advprog-2026-A04-project/Frontend`

---

## Architecture Overview

Project ini menggunakan struktur berlapis agar tanggung jawab kode terpisah dengan jelas.

### Backend layering
- **Controller**: menerima request dan mengembalikan response
- **Service**: menangani business logic order
- **Repository**: akses data ke database
- **Entity/Model**: representasi data order
- **DTO**: format request/response
- **Config**: konfigurasi aplikasi
- **Exception/Common Response**: standar error dan format response seragam

### Frontend
Frontend menggunakan React untuk:
- menampilkan status health service
- mengirim request checkout
- menampilkan daftar order

---

## CI Quality Gates

### Workflow files
- **CI:** `.github/workflows/ci.yml`
- **CodeQL:** `.github/workflows/codeql.yml`
- **CD:** `.github/workflows/cd.yml`
- **Scorecard:** `.github/workflows/scorecard.yml`

### CI includes
- Build validation
- Test execution
- Lint / quality checks
- Basic verification that the service is runnable

### CodeQL includes
- Security and code quality analysis for repository code
- Runs according to workflow trigger configuration

### CD includes
- Deployment pipeline for the service
- Used for deployment to staging / production depending on branch setup

### Scorecard includes
- Repository security posture checks
- Best-practice scanning for the project

---

## Frontend-Backend-DB Integration

### Current integration path
- Frontend sends request to backend API
- Controller receives request
- Service layer executes business logic
- Repository persists order data
- Database stores order and order items
- Frontend fetches and displays the latest order data

### Current simple demo flow
1. User opens frontend
2. Frontend checks service health using actuator
3. User fills checkout form
4. Frontend sends checkout request to backend
5. Backend creates order and stores it into database
6. Frontend refreshes order list
7. Created order appears on screen

### Status for milestone 25%
- Backend Order is already reachable
- Checkout flow is already available
- Order is persisted to DB
- `voucherCode` field is already included in request payload
- Voucher behavior is still minimal / stub

## Main Endpoints

### Health Check
- `GET /actuator/health`

Digunakan untuk memverifikasi bahwa service sedang hidup dan dapat diakses.

---

### Create Order
- `POST /orders/checkout`

Membuat order baru dan menyimpannya ke database.

#### Example request
```json
{
  "address": "Jl. Mawar No. 1",
  "voucherCode": "PROMO10",
  "items": [
    {
      "productId": 2,
      "qty": 1
    }
  ]
}
