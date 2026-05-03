# Order Service

Order orchestration service for JSON Milestone `75%`.

## Scope

- `POST /orders/checkout`
- `GET /orders/my`
- `GET /orders/my/active`
- `GET /orders/jastiper`
- `GET /orders/admin`
- `GET /orders/{id}`
- `PATCH /orders/{id}/status`
- `POST /orders/{id}/cancel`
- `POST /orders/{id}/rating`
- `GET /actuator/health`

Lifecycle support:

- `PAID -> PURCHASED -> SHIPPED -> COMPLETED`
- `CANCELLED` with refund
- invalid transition rejection
- idempotent cancellation refund

Checkout orchestration remains here:

1. read product and stock from Inventory
2. validate voucher with Voucher/Promo
3. validate and deduct wallet balance through Wallet
4. persist order state
5. restore/compensate on downstream failure

## Local Run

Prerequisites:

- Java `21`

Run from `backend/`:

```bash
./gradlew bootRun
```

PowerShell:

```powershell
.\gradlew.bat bootRun
```

Default local URL:

- `http://localhost:8080`

## Environment Variables

- `PORT`
- `DB_URL`
- `DB_DRIVER`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_CORS_ALLOWED_ORIGINS`
- `JWT_SECRET`
- `INTERNAL_API_TOKEN`
- `INVENTORY_SERVICE_BASE_URL`
- `WALLET_SERVICE_BASE_URL`
- `VOUCHER_SERVICE_BASE_URL`

Defaults use an H2 file database under `/tmp`.

## Test

```bash
cd backend
./gradlew test
```

Coverage includes:

- checkout orchestration
- lifecycle transition validation
- cancel/refund behavior
- refund idempotency
- rating rules
- active/jastiper/admin order views

## Deployment

Target platform: Google Cloud Run.

Basic deploy:

```bash
gcloud run deploy order-api --source . --region us-central1 --allow-unauthenticated --max-instances=1
```

The service should keep the already configured Cloud Run env values for:

- shared `JWT_SECRET`
- `INTERNAL_API_TOKEN`
- Inventory base URL
- Wallet base URL
- Voucher base URL
- allowed frontend origins

## Risks

- The service expects downstream Inventory, Wallet, and Voucher URLs to stay aligned with the deployed demo stack.
- Cancellation is intentionally limited to `PAID` and `PURCHASED`.
