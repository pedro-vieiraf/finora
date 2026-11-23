# Finora

Finora is a personal finance application for tracking bank accounts, cash, transactions, recurring commitments, and credit cards. It combines accounts you manage yourself with Open Finance accounts synchronized through Pluggy. The Expo app is designed for daily use on a phone and also includes a desktop web layout.

## What you can do

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
- Imported transactions preserve their original financial data. You can add a local category and custom description. A third-party Pix transfer is an expense when it represents an outgoing payment, while a reconciled transfer between your own accounts is internal.
- Credit card purchases count as spending. For a manual credit card, the account balance changes when you register the invoice payment.
- Connected cards show bill data and upcoming installments when the provider supplies them. The dashboard labels estimates derived from known installments and identifies the items behind each forecast. Future recurring charges appear as the provider reports them.
- Automatic Banrisul CDB applications and redemptions are excluded from operational views. The imported records and the bank-reported balance are preserved.

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
4. Run `confirm` with the reviewed arguments to write the initial import. The CLI needs a reachable, initialized PostgreSQL database and the appropriate Spring datasource configuration for your environment. Run the API first to initialize its schema.
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
| `/accounts` | Create, list, and manage financial accounts. |
| `/categories` | Manage transaction categories. |
| `/transactions` | List transactions and create or manage manual entries. |
| `PUT /transactions/{id}/details` | Update the local category and custom description of an imported transaction. |
| `/recurrences` | Manage scheduled transactions. |
| `/credit-cards/{cardId}/purchases` | Manage manual purchases and installments. |
| `/credit-cards/{cardId}/invoices` | Read manual card invoices and access their payments. |
| `GET /credit-cards/open-finance/bills` | Read connected card bills and itemized forecasts. |

The API is intended for a trusted, personal environment. Multi-user authentication and authorization are outside the current scope.

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

[Version 2.0.0](https://github.com/pedro-vieiraf/finora/tree/v2.0.0) introduced Open Finance support. The current branch also includes the monthly invoice drill-down added after that release.

Finora is licensed under the [MIT License](LICENSE).
