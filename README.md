# Order Service

Order orchestration service for Milestone `25%` and `50%`.

## Deployed URL

- `https://order-api-383620816191.us-central1.run.app`

## Implemented Scope

- `GET /actuator/health`
- `POST /orders/checkout`
- `GET /orders/my`
- `GET /orders/{id}`

Checkout flow:
1. Read product snapshot and stock from Inventory.
2. Validate voucher with Voucher/Promo.
3. Calculate final total.
4. Validate wallet balance.
5. Deduct wallet balance.
6. Reduce stock.
7. Claim voucher quota.
8. Persist order as `PAID`.

If a failure happens after the order is created, Order compensates by refunding Wallet and restoring Inventory stock before marking the order as `FAILED`.

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

Defaults are configured for an H2 file database under `/tmp`.

## Test

```bash
cd backend
./gradlew test
```

Includes:
- real HTTP client integration coverage for checkout orchestration

## Cloud Run Deploy

```bash
gcloud run deploy order-api --source . --region us-central1 --allow-unauthenticated --max-instances=1 \
  --set-env-vars APP_CORS_ALLOWED_ORIGINS=https://advprog-frontend-m25-m50-383620816191.us-central1.run.app \
  --set-env-vars JWT_SECRET=<shared-jwt-secret> \
  --set-env-vars INTERNAL_API_TOKEN=<shared-internal-token> \
  --set-env-vars INVENTORY_SERVICE_BASE_URL=https://inventory-api-383620816191.us-central1.run.app \
  --set-env-vars WALLET_SERVICE_BASE_URL=https://wallet-api-383620816191.us-central1.run.app \
  --set-env-vars VOUCHER_SERVICE_BASE_URL=https://voucher-promo-api-383620816191.us-central1.run.app
```

## Notes

- Scope is intentionally limited to Milestone `25%` and `50%`.
- Full order lifecycle, refund workflows, and later milestones are intentionally excluded.
