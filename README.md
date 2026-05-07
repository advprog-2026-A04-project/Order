# Order Service

Order orchestration service for Milestone `25%`, `50%`, `75%`, and `100%`.

## Deployed URL

- `https://order-api-383620816191.us-central1.run.app`

## Implemented Scope

### Endpoints

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/actuator/health` | Public | Health check |
| `POST` | `/orders/checkout` | TITIPER | Create and pay for an order |
| `GET` | `/orders/my` | TITIPER | List all orders by the current buyer |
| `GET` | `/orders/my/active` | TITIPER | List active orders (excludes COMPLETED, CANCELLED, FAILED) |
| `GET` | `/orders/{id}` | TITIPER, JASTIPER, ADMIN | Get order detail |
| `PATCH` | `/orders/{id}/status` | TITIPER, JASTIPER, ADMIN | Advance order status |
| `POST` | `/orders/{id}/cancel` | JASTIPER, ADMIN | Cancel an order with automatic refund |
| `POST` | `/orders/{id}/rating` | TITIPER | Submit product and jastiper rating after completion |
| `GET` | `/orders/jastiper` | JASTIPER | List orders assigned to the current jastiper |
| `GET` | `/orders/admin` | ADMIN | List all orders for monitoring |

### Order Lifecycle

```
PENDING → PAID → PURCHASED → SHIPPED → COMPLETED
                                    ↘
                              CANCELLED (with refund)
```

Status transition rules:
- **JASTIPER**: can advance `PAID → PURCHASED → SHIPPED`
- **TITIPER**: can confirm `SHIPPED → COMPLETED`
- **ADMIN**: can advance any status
- Transitions that skip steps or go backwards are rejected

### Checkout Flow

1. Read product snapshot and stock from Inventory.
2. Validate voucher with Voucher/Promo.
3. Calculate final total (subtotal − discount).
4. Validate wallet balance.
5. Deduct wallet balance.
6. Reduce inventory stock.
7. Claim voucher quota.
8. Persist order as `PAID`.

If a failure occurs after the order is created, the service compensates automatically by refunding the wallet and restoring inventory stock before marking the order as `FAILED`.

### Cancel & Refund

- Only **JASTIPER** (assigned) or **ADMIN** can cancel an order.
- Cancellation is allowed when the order is in `PAID` or `PURCHASED` state.
- If the order was `PAID`, wallet balance is refunded and inventory stock is restored automatically.
- Refund is idempotent — calling cancel on an already-cancelled order returns the current state without re-processing.

### Rating

- Only available after the order reaches `COMPLETED`.
- **TITIPER** submits separate ratings for the product (1–5) and the jastiper (1–5), plus an optional comment.
- Each order can only be rated once.

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

| Variable | Description |
|----------|-------------|
| `PORT` | Server port |
| `DB_URL` | JDBC database URL |
| `DB_DRIVER` | JDBC driver class |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed CORS origins |
| `JWT_SECRET` | Shared JWT secret for token verification |
| `INTERNAL_API_TOKEN` | Token for internal service-to-service calls |
| `INVENTORY_SERVICE_BASE_URL` | Base URL of the Inventory service |
| `WALLET_SERVICE_BASE_URL` | Base URL of the Wallet service |
| `VOUCHER_SERVICE_BASE_URL` | Base URL of the Voucher/Promo service |

Defaults are configured for an H2 file database under `/tmp`.

## Test

```bash
cd backend
./gradlew test
```

Test coverage includes:

- **Checkout**: successful flow, insufficient wallet balance, voucher claim failure, compensation on error
- **Order lifecycle**: status transitions (valid and invalid), role-based access control per transition
- **Cancel & refund**: automatic wallet refund and stock restoration, idempotency on re-cancel
- **Rating**: validation (range, duplicate, wrong status), role enforcement
- **Jastiper view**: list assigned orders, forbidden access for other roles
- **Admin monitoring**: list all orders, forbidden access for non-admin roles
- **Active orders**: filter for non-terminal statuses
- **HTTP client integration**: real WireMock-based coverage for Inventory, Wallet, and Voucher clients

Coverage threshold: **90% line and branch coverage** enforced via JaCoCo.

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
