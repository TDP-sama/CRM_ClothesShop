# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

Sử dụng tiếng Việt để trả lời.

## Commands

Backend (Spring Boot 4.1, Spring Security 7, Java 21, Maven), run from repo root:
- Test: `mvn -f backend/pom.xml test`
- Single test: `mvn -f backend/pom.xml test -Dtest=ShopFlowIntegrationTest#placingAnOrderUsesServerPricesAndTakesStock`
- Run (port 8080): `mvn -f backend/pom.xml spring-boot:run`
- API docs: Swagger UI at http://localhost:8080/swagger-ui.html (springdoc; copies the XSRF-TOKEN cookie into the header automatically)

Frontend (React 19 + React Router 7 + Vite), run from `frontend/`:
- Install: `npm install`
- Dev server (port 5173): `npm run dev` — in PowerShell do not pass `npm run dev -- --port X`; PowerShell strips `--` and Vite then treats the port as its root directory
- Production build: `npm run build`

## Architecture

"MODA", a fashion web shop. Two independent projects; the SPA talks to the API same-origin because Vite proxies `/api` → `localhost:8080` (`frontend/vite.config.js`). That is why the backend has no CORS config and why cookie auth works.

### Backend (`backend/`, package `com.example.shop`)
- `catalog/`: `Category` → `Product` (soft-deleted via `active=false`, never hard-deleted) → `ProductVariant` (one size/color with its own `stock`). Prices are whole VND (`long`); `compareAtPrice` > `price` means "on sale". `slug` is the ASCII form of the name and doubles as accent-insensitive search (`CatalogService.specificationFor`). `DemoDataSeeder` fills an empty catalog on startup (`SEED_DEMO_DATA=false` to disable).
- `order/`: COD checkout. `OrderService.place` locks the variant rows (`findAllForUpdate`, pessimistic) and takes prices/stock from the DB, never from the client. `OrderItem` copies product data and stores product/variant ids as plain columns (no FKs), so catalog edits never affect past orders. Status workflow is `OrderStatus.next()`; cancelling restocks. Shipping rule: `OrderService.SHIPPING_FEE` / `FREE_SHIPPING_THRESHOLD`, exposed to the SPA via `GET /api/config`.
- `review/`: one review per user per product, only after an order containing it is `DELIVERED`.
- `report/`: `ReportService` aggregates in Java (not SQL, to stay H2/PostgreSQL-neutral) over non-cancelled orders of the last 7/30/90/365 days plus the previous period of equal length. Revenue = order subtotal (goods, no shipping); days are bucketed in `Asia/Ho_Chi_Minh`, by month for 365.
- `inventory/`: **all stock changes go through `InventoryService.change`** (on a row locked via `findAllForUpdate`), which writes a `StockMovement` (ledger: type INITIAL/RECEIPT/SALE/RETURN/ADJUSTMENT, before/after, reference code like PN…/DH…, actor email). Orders call it for SALE/RETURN; `GoodsReceipt` (phiếu nhập) for RECEIPT; adjustments set the counted quantity. The product form never changes the stock of an existing variant: `AdminCatalogService` only gives *new* variants their initial stock through `InventoryService.open` (which also generates the SKU). On startup `InventoryService.run` (ordered after `DemoDataSeeder`) gives untracked variants a SKU and an INITIAL movement, so the ledger always explains the current stock. Movements and receipt lines store variant ids as plain columns plus copied product data, like order items.
- `auth/`: session-cookie auth.
  - `SecurityConfig`: public = auth endpoints, `GET` catalog/config, Swagger, `/error`; everything else needs login. Each `/api/admin/...` path maps to an `AdminSection` (stats → DASHBOARD, products, categories, orders, inventory, reports); `/api/admin/users` (staff management) is ADMIN only. These checks call `UserService.canAccess`/`isAdmin`, i.e. read the database on every request instead of the session's authorities, so permission changes apply immediately. 401/403 from the filter chain are written as problem+json there. CSRF is `csrf.spa()` with a shared `CookieCsrfTokenRepository` bean: clients echo the `XSRF-TOKEN` cookie in `X-XSRF-TOKEN` on every write, **including login and register**. `NullRequestCache` keeps anonymous 401s from creating sessions. Logout (`POST /api/auth/logout`) is handled by the Spring Security filter, so it has no controller method and does not appear in Swagger.
  - `AuthController.login` authenticates manually via the `AuthenticationManager`, then calls `changeSessionId()`, saves the `SecurityContext` to the session, and rotates the CSRF token itself (Spring's login-filter strategies don't run).
  - Roles: `CUSTOMER`, `STAFF` (admin area limited to the `AdminSection`s stored in `user_permissions`), `ADMIN` (everything). ADMIN comes only from `ADMIN_EMAILS` (on registration and at startup, `UserService.run`); `UserService.updateAccess` (used by `AdminUserController`) can only set CUSTOMER/STAFF and refuses to touch admins. `/api/auth/me` returns the effective `permissions`, which the SPA uses to show sections.
  - `common/SchemaFixes` drops the CHECK constraint Hibernate generated for `app_users.role` before STAFF existed (`ddl-auto=update` never updates such constraints); adding a value to any persisted enum needs the same treatment.
  - Emails are normalized (`UserService.normalizeEmail`) before validation and every lookup. Passwords use the delegating encoder (`{bcrypt}`); `@MaxUtf8Bytes(72)` enforces BCrypt's byte (not character) limit.
- `common/`: `ApiExceptionHandler` makes every MVC error problem+json, with an `errors` map (field → Vietnamese message) for validation failures; `PageResponse` is the JSON shape of all paged lists.
- Schema is managed by Hibernate `ddl-auto=update`; there are no migrations. New NOT NULL columns on existing tables need a `@ColumnDefault` (see `AppUser.role`).

### Configuration and secrets
- The database is PostgreSQL on Neon. `spring.datasource.url=${DATABASE_URL}`, where `DATABASE_URL` is a full JDBC URL with credentials (`jdbc:postgresql://<host>/neondb?user=...&password=...&sslmode=require`).
- `application.properties` imports the git-ignored `backend/.env` (`spring.config.import`), so `DATABASE_URL`, `ADMIN_EMAILS` etc. can come from that file or from the environment. Never put credentials in `application.properties`.
- Other env vars: `SESSION_COOKIE_SECURE` (default `false` for local http; must be `true` behind HTTPS), `SESSION_TIMEOUT` (default `2h`), `SEED_DEMO_DATA` (default `true`).

### Frontend (`frontend/src`)
- `api.js` is the only place that calls `fetch`: it bootstraps/reads the XSRF cookie, retries once on a missing token, and turns problem+json into `ApiError` (`status`, `message`, `fieldErrors`).
- Global state in `state/`: `auth` (session user via `/api/auth/me`), `cart` (kept in localStorage; display-only prices), `theme`, `toast`.
- Themes: every visual decision in `App.css` is a CSS variable defined per `[data-theme="co-ban|toi-gian|nang-dong|cao-cap"]`. The Settings drawer (`Layout.jsx`) switches `data-theme` on `<html>`; the choice is saved in localStorage (an inline script in `index.html` applies it before first paint) and can be forced with `?theme=<id>`. Add a theme by adding a variable block in `App.css` and an entry in `THEMES` (`state/theme.jsx`).
- Charts (`components/charts.jsx`) are hand-built SVG/CSS, no chart library. They draw with `--series-1`, defined per theme in `App.css` and validated as a data color against that theme's surface (near-black/gray accents fail, so most themes use blue). Text in charts uses text tokens, never the series color; every chart card has a table view.
- Routes are Vietnamese paths (`/san-pham`, `/gio-hang`, `/thanh-toan`, `/tai-khoan/don-hang`, `/quan-tri/...`) in `App.jsx`; catalog filters live in the URL query string. `RequireAuth` guards customer pages; `backOffice`, `section="ORDERS"` etc. and `adminOnly` guard the admin area to mirror the server rules. Section ids/labels/paths for the admin sidebar live in `ADMIN_SECTIONS` (`lib/format.js`).

### Tests (`backend/src/test`)
- All are `@SpringBootTest` on in-memory H2 (each class sets its own `spring.datasource.url`, so they never touch Neon even when `.env` exists) with `app.seed-demo-data=false`.
- `ShopFlowIntegrationTest` authenticates with spring-security-test `user(...)` mock principals whose names match `AppUser` rows created in `@BeforeEach`.
- spring-security-test's `.with(csrf())` replaces the CSRF token repository inside the cached application context. Tests that exercise the real cookie/header CSRF flow must stay in a separate context (as `BrowserCsrfFlowTest` does) and must not use `csrf()`.
