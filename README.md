# Finora

Finora is a personal finance application for tracking bank accounts, cash, transactions, recurring commitments, and credit cards. It combines accounts you manage yourself with Open Finance accounts synchronized through Pluggy. The Expo app is designed for daily use on a phone and also includes a desktop web layout.

**Latest release · [v2.1.0](https://github.com/pedro-vieiraf/finora/tree/v2.1.0)**

## Features

| Area | Features |
| --- | --- |
| Accounts | Create and manage manual accounts, distinguish them from connected accounts, and view balances and transaction history. |
| Transactions | Record income, expenses, and internal transfers. Organize transactions with categories and edit manual entries. |
| Recurring transactions | Schedule recurring income and expenses and review upcoming commitments. |
| Credit cards | Record manual purchases and installments, review invoices, and register invoice payments. |
| Open Finance | Import posted transactions from a starting date, synchronize balances and transactions, and reconcile reviewed internal transfers. |
| Dashboard | Review balances, spending, upcoming commitments, and card bills. Select a month in the six-month invoice chart to see its purchases, installments, and credits. |

### How connected accounts work

- The bank-reported balance is authoritative. The import tracks provider transaction IDs to prevent duplicate entries, and the scheduler brings in newly posted transactions.
- Pending bank debits appear in the statement and expense totals. Confirmation replaces the pending entry using the same provider identifier; the bank-reported balance remains authoritative.
- You can track a confirmed bank or card charge as a subscription to estimate the next monthly charge without generating a duplicate transaction.
- Imported transactions preserve their original financial data. You can add a local category and custom description. A third-party Pix transfer is an expense when it represents an outgoing payment, while a reconciled transfer between your own accounts is internal.
- Credit card purchases count as spending. For a manual credit card, the account balance changes when you register the invoice payment.
- Connected cards show bill data and upcoming installments when the provider supplies them. The dashboard labels estimates derived from known installments and identifies the items behind each forecast. Future recurring charges appear as the provider reports them.
- Automatic Banrisul CDB applications and redemptions remain visible in the statement and are excluded from operating totals. The imported records and the bank-reported balance are preserved.

## Quick start

### Prerequisites

- Docker with Docker Compose for the API and PostgreSQL.
- Node.js 24 and npm for the Expo frontend.
- Java 21 and Maven 3.9+ if you want to run the backend tests or the Open Finance CLI.

### Start the API

```bash
git clone https://github.com/pedro-vieiraf/finora.git
cd finora
docker compose up -d --build
curl http://localhost:8080/accounts
```

The API listens on port 8080. PostgreSQL stores its data in a named Docker volume. Use `docker compose down` to stop the stack while preserving that volume.

For an existing installation, keep using its current Compose project name. The checkout directory normally determines that name, so changing the folder can cause Compose to select a different database volume. The GitHub rename only requires updating the remote URL.

### Start the app

Create `frontend/.env` for your own checkout:

```dotenv
EXPO_PUBLIC_API_URL=http://localhost:8080
```

For a physical phone, use the computer's reachable LAN address in place of `localhost`. On the standard Android emulator, the host address is usually `10.0.2.2`. You can set `EXPO_PUBLIC_API_URL_ANDROID` to override the URL on Android while keeping the web URL for the browser.

```bash
cd frontend
npm ci
npx expo start
```

Open the project on your device or press `w` in the Expo terminal to run the web layout. Expo public variables are bundled into the client. Keep provider credentials on the backend.

## Hosted deployment

Finora's hosted configuration uses an Android APK, a containerized Spring Boot API on Render, and PostgreSQL on Neon.

```text
Android app ── HTTPS ──> Spring Boot API (Render) ── verified TLS ──> PostgreSQL (Neon)
                               │
                               └──> Pluggy (bank transactions, balances, and card bills)
```

1. Provision a Neon database and initialize the Finora schema before deploying the API. The `neon` profile leaves schema initialization and Hibernate schema updates disabled. Existing installations can transfer their data and Open Finance links separately.
2. Create a Docker Web Service on Render from `backend/` using its `Dockerfile`. Set the variables below in the service environment. The default TCP health check works with the protected API.
3. Configure the Expo `preview` environment with your HTTPS API URL and build an APK. The profile enables Finora's sign-in screen. See the [Android build guide](frontend/README.md#build-an-android-apk).

| Server variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Set to `neon` for the hosted database profile. |
| `SPRING_DATASOURCE_URL` | Neon JDBC URL with verified TLS, such as `jdbc:postgresql://HOST/DATABASE?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`. |
| `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` | Neon role and password. |
| `SERVER_PORT` | HTTP port. Set to `10000` on Render. |
| `FINORA_API_TOKEN` | Private 32-byte access key. Generate it with `openssl rand -hex 32`. |
| `PLUGGY_CLIENT_ID` and `PLUGGY_CLIENT_SECRET` | Optional server-side credentials for the Pluggy integration. |
| `LEDGER_OPEN_FINANCE_SCHEDULER_ENABLED` | Set to `true` to poll Pluggy for existing bank links. |

The hosted API requires the access key as a bearer token. Enter the key on the Finora sign-in screen. On Android, Finora can remember the key in the device’s secure storage and unlock it with biometrics. The key is otherwise kept only for the current session. The APK contains the public API URL and sign-in flag. Local Docker development can run without a key.

On Render's free plan, the API can sleep between requests. A cold start can delay the first screen, and scheduled jobs run only while the API is awake. The Neon database persists independently. Pluggy controls how frequently it collects bank data, and Finora imports transactions once the provider makes them available.

### Test the Neon connection locally

Use the [Neon CLI](https://neon.com/docs/connect/connect-from-any-app) to link your own project and pull its connection variables. Keep the generated files private. With an initialized Neon database, run these commands from the repository root:

```bash
npm ci
npm --prefix frontend ci
npm run build
# Supply FINORA_API_TOKEN to the process environment before starting.
SERVER_PORT=8081 npm run start:neon
```

The build packages the Spring Boot JAR and exports an Android JavaScript bundle. The launcher reads the pooled `DATABASE_URL` and configures PostgreSQL JDBC with verified TLS.

## Optional Open Finance setup

The manual workflow works with no Pluggy credentials. For Open Finance, start with a Pluggy application and provider-authorized connections. Collect the item ID and bank account ID for each account you want to import.

1. Store `PLUGGY_CLIENT_ID` and `PLUGGY_CLIENT_SECRET` in a private local environment file outside the repository. Docker Compose looks for `~/.config/finora/open-finance.env` by default. Set `FINORA_OPEN_FINANCE_ENV_FILE` when running Compose to use another path.
2. Load the two Pluggy variables into your terminal environment before using the CLI. From the repository root, build the package and open the import help:

   ```bash
   cd backend
   mvn -B -DskipTests package

   java -Dloader.main=com.pedro.ledger.infrastructure.openfinance.PluggyImportCommand \
     -cp target/finora-0.0.1-SNAPSHOT.jar \
     org.springframework.boot.loader.launch.PropertiesLauncher --help
   ```

3. Run the command with `preview <from> <to> <name> <itemId> <accountId>`. Repeat the last three arguments for each account. Dates are inclusive UTC dates in `YYYY-MM-DD` format. Preview queries the provider without writing to PostgreSQL. Review suspected transfer pairs using `review` and pass explicit `--transfer` or `--separate` decisions where required.
4. Run `confirm` with the reviewed arguments to write the initial import. The CLI needs a reachable, initialized PostgreSQL database and the appropriate Spring datasource configuration for your environment. For local Docker development, start the API once to initialize its schema.
5. Add `LEDGER_OPEN_FINANCE_SCHEDULER_ENABLED=true` to the private Compose environment file and recreate the API service with `docker compose up -d --force-recreate app` from the repository root. The default sync delay is six hours after each run, with an initial one-minute delay after startup. Existing scheduler keys remain compatible with older installations.

## Architecture

| Location | Responsibility |
| --- | --- |
| [`backend/`](backend/) | Java 21, Spring Boot, Spring Data JPA, Hibernate, Maven, and PostgreSQL. |
| [`backend/.../domain/`](backend/src/main/java/com/pedro/ledger/domain/) | Accounts, transactions, money, recurrences, and credit card business rules. Money uses `BigDecimal` with explicit currency. |
| [`backend/.../application/`](backend/src/main/java/com/pedro/ledger/application/) | Use cases that coordinate domain logic, repositories, and transaction boundaries. |
| [`backend/.../infrastructure/`](backend/src/main/java/com/pedro/ledger/infrastructure/) | REST controllers, PostgreSQL persistence adapters, and the Pluggy integration. |
| [`frontend/src/`](frontend/src/) | React Native, Expo Router, and TypeScript. Routes, UI components, providers, and API clients live here. |
| [`compose.yaml`](compose.yaml) | Local API and PostgreSQL services. |

The backend serializes concurrent balance mutations with database locks. Account deactivation retains transaction history. The Java package root and some database and configuration identifiers retain their original technical names so existing local installations and commands can be migrated separately if needed.

### REST API overview

| Route | Purpose |
| --- | --- |
| `GET /auth/session` | Verify the access key for a hosted session. |
| `/accounts` | Create, list, and manage financial accounts. |
| `/categories` | Manage transaction categories. |
| `/transactions` | List transactions and create or manage manual entries. |
| `PUT /transactions/{id}/details` | Update the local category and custom description of an imported transaction. |
| `/recurrences` | Manage scheduled transactions. |
| `/credit-cards/{cardId}/purchases` | Manage manual purchases and installments. |
| `/credit-cards/{cardId}/invoices` | Read manual card invoices and access their payments. |
| `GET /credit-cards/open-finance/bills` | Read connected card bills and itemized forecasts. |

## Testing

Run the backend suite from the repository root. Docker is required for the PostgreSQL integration tests that use Testcontainers.

```bash
cd backend
mvn -B verify
```

Run the frontend checks from `frontend/`:

```bash
npm test
node --experimental-strip-types --test src/utils/open-finance-card-bills.test.ts src/utils/operational-transactions.test.ts
npx tsc --noEmit
npm run lint
```

The automated checks cover financial rules, balance concurrency, REST and persistence behavior, Open Finance import and forecasts, and dashboard calculations. Live provider testing requires a locally authorized Pluggy connection.

## Releases and license

- [v2.1.0](https://github.com/pedro-vieiraf/finora/tree/v2.1.0) adds authenticated hosting with Neon and Render, direct Android APK installation, and an itemized monthly invoice chart.
- [v2.0.0](https://github.com/pedro-vieiraf/finora/tree/v2.0.0) introduced Open Finance via Pluggy, including bank imports, synchronization, and connected card forecasts.

Finora is licensed under the [MIT License](LICENSE).
