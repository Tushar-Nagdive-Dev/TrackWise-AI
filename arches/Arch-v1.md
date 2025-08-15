# TrackWise AI — Architecture & Delivery Plan v1.0

*Last updated: 15 Aug 2025 (IST)*

---

## 1) Vision & Product North Star

**TrackWise AI** is a privacy-first, AI-powered money tracker that turns raw financial exhaust (bank SMS/UPI, statements, invoices, PDFs, CSVs) into clear insights, forecasts, and actionable nudges for individuals and families. It blends deterministic rules, ML, and a conversational copilot (English/Hinglish) to help users spend smarter, catch mistakes/fraud early, and reach goals predictably.

**North Star Metric (NSM):** “Percent of monthly income automatically tracked, categorized, and budgeted with at least one helpful nudge accepted.”

**Primary personas:**

* **Solo Professional** (busy, multi-bank, wants zero-maintenance automation)
* **Family Finance Lead** (needs shared budgets, envelope-style planning, bill tracking)
* **Power User** (wants granular exports, custom rules, APIs)

---

## 2) Core Use Cases (MVP → Advanced)

1. **Multi-source ingestion**: Bank/UPI SMS, email statements (IMAP), PDF/CSV uploads, app/web manual entries, WhatsApp receipts (forward-to-email).
2. **Normalization & enrichment**: Parse → dedupe → standardize → detect merchant → geotag → FX normalization → attach documents.
3. **Auto-categorization**: Rules + ML hybrid classifier with feedback loop.
4. **Budgets & goals**: Category budgets, envelopes, alerts, and goal simulation.
5. **Anomaly & fraud-ish signals**: Spike detection, duplicate charges, subscription price drift, merchant mismatch.
6. **Subscriptions & bills**: Detect recurring patterns; predict next due date; reminders & autopay checks.
7. **Cash-flow forecasting**: 30/60/90-day balance projection; scenario “what-if”.
8. **Copilot**: Ask in English/Hinglish; RAG over user’s ledger & docs; proactive nudges.
9. **Privacy-first controls**: On-device options, end-to-end encryption-at-rest/in-flight, local redaction.

---

## 3) Non-Functional Requirements

* **Privacy & security**: Zero-trust, field-level encryption for sensitive PII; SOC2-ready logging; opt-in telemetry.
* **Accuracy**: ≥95% category precision on top 30 categories after user feedback loop.
* **Latency**: P95 < 1.5s for common reads; async for heavy ETL/ML jobs.
* **Reliability**: 99.9% API uptime; idempotent ingestion; at-least-once processing.
* **Extensibility**: Plug-in connectors for banks, wallets, payroll; pluggable ML models.

---

## 4) High-Level Architecture (Event-Driven, Modular)

```
Sources → Ingestion Adapters → Event Bus → Processing Pipeline → Ledger & Analytics → APIs → Apps/Copilot
```

### 4.1 Logical Components

* **Client Apps**: Angular Web (macOS-style), Desktop (Electron), Mobile (Capacitor/Android bridge for SMS ingestion optional).
* **API Gateway**: Spring Cloud Gateway + rate limiting + JWT/Keycloak.
* **AuthN/Z**: Keycloak (OIDC), roles: USER, FAMILY\_ADMIN, AUDITOR; device binding; TOTP.
* **Ingestion Adapters**:

    * **SMS/UPI** (Android bridge), **Email** (IMAP fetcher), **File** (PDF/CSV), **Webhook** (Stripe/UPI/Payout), **Manual**.
* **Event Bus**: Apache Kafka (topics: `raw.txn`, `parsed.txn`, `classified.txn`, `alerts`, `nudges`, `docs.ingested`).
* **Processing Pipeline**:

    * **Parsing & Normalization Service** (Spring Boot): schema mapping, idempotency keys.
    * **Enrichment Service**: merchant resolver, geo, MCC, FX, receipt linking.
    * **Classification Service**: rules engine + ML classifier; feedback writes.
    * **Recurring/Subscription Service**: pattern mining, drift detection.
    * **Anomaly Service**: spike/duplicate/outlier (IsolationForest/Z-scores), rule-based fraud-ish flags.
    * **Forecasting Service**: cashflow (Prophet/LSTM/ARIMA), bill schedule.
    * **Nudges & Recommendations**: policy engine (DSL) + experiment flags.
* **Data Layer**:

    * **OLTP**: PostgreSQL (ledger, users, budgets, goals). Extensions: `pgcrypto`, `pg_partman`, `pgvector` (optional)
    * **Object Storage**: MinIO/S3 for PDFs/receipts/exports.
    * **Search/Vector**: `pgvector` for semantic merchant matching & RAG memory.
    * **Warehouse**: DuckDB/ClickHouse (fast analytics) or Postgres OLAP schema; dbt transformations.
    * **Cache**: Redis (sessions, rate limits, feature flags).
* **Copilot & NLP**:

    * **Prompt Orchestrator** (Spring AI): tool-calling to Ledger/Budget APIs.
    * **RAG**: embeddings over user’s statements & receipts; safety filters; Hinglish intent classifier.
* **Observability**: OpenTelemetry → Prometheus + Grafana; ELK for logs; Kafka lag monitoring.
* **DevOps**: Docker, K8s, GitLab CI, Helm; secrets via Vault; SAST/DAST gates.

---

## 5) Domain Model (Key Entities)

* **User**, **Household** (shared), **BankAccount**, **Transaction** (raw→normalized→classified states), **Merchant**, **Category** (hierarchical), **Budget**, **Goal**, **Subscription** (pattern + schedule), **Bill** (due cycles), **Alert/Nudge**, **Document** (receipt/statement), **Feedback** (label corrections), **FeatureFlag**.

**Transaction (normalized) — essential fields:**
`id, user_id, account_id, txn_ts, amount, currency, direction (debit/credit), merchant_id, merchant_text, category_id, subcategory_id, channel (UPI/IMPS/Card/Cash), geo, tags[], confidence, source_doc_id, raw_hash`

---

## 6) Data Flow (Happy Path)

1. **Ingest**: SMS/Email/File → Adapter writes `raw.txn` with idempotency key.
2. **Parse/Normalize**: Parsing Svc consumes `raw.txn`, outputs `parsed.txn`.
3. **Enrich**: Enrichment Svc attaches merchant, MCC, FX; writes to DB + `parsed.txn`.
4. **Classify**: Classification Svc applies Rules → ML; emits `classified.txn` & stores label + confidence.
5. **Detect**: Subscription & Anomaly Svcs read `classified.txn`; persist derived entities; publish `alerts`.
6. **Forecast & Nudge**: Forecasting Svc reads ledger; Nudges Svc maps insights to user-facing suggestions.
7. **Copilot**: Uses RAG + tool calls to answer queries; writes explanations for auditability.

---

## 7) ML Strategy (Hybrid & Iterative)

* **Categorization**: start with rules (regex/templates per bank SMS), then fine-tune a small text classifier; leverage `pgvector` for nearest-merchant match.
* **Subscription detection**: interval mining (auto-correlation), day-of-month clustering, fuzzy merchant grouping.
* **Anomaly**: rolling z-score on spend deltas + Isolation Forest on merchant-amount pairs.
* **Forecasting**: Prophet baseline; upgrade to LSTM only if needed; cross-validated per user.
* **Feedback loop**: user correction → training queue → nightly re-train; per-user adapter via embeddings.

---

## 8) Security & Privacy

* **Encryption**: TLS everywhere; AES-256 at rest; field-level encryption for PII; tokenization for account numbers; signed URLs for receipts.
* **Auth**: Keycloak OIDC; device binding; TOTP; session limits; family sharing via invites.
* **Access controls**: resource-scoped RBAC; household boundaries enforced at SQL row level (RLS) or service layer.
* **Compliance-ready**: audit logs (immutable), data retention policies, export/delete-my-data flows.
* **On-device mode** (future): local-only ledger with optional cloud backup.

---

## 9) Public APIs (selected)

**/v1/ingest**: POST files (PDF/CSV), email connect, SMS push

**/v1/transactions**: list/query by time, category, merchant; bulk edit; export CSV/Excel

**/v1/budgets**: CRUD budgets; get utilization; alerts

**/v1/subscriptions**: list; pause; price-change history

**/v1/insights**: GET anomalies, nudges, forecasts

**/v1/copilot**: POST chat {message, tools\[]}; returns structured answer + citations

---

## 10) Tech Stack (aligned to team skillset)

* **Backend**: Java 21, Spring Boot 3.x, Spring Cloud, Spring Security, Spring AI
* **Data**: PostgreSQL 16 (with `pgvector`, `pg_partman`), Redis, MinIO, Kafka 3.x, dbt (optional), DuckDB/ClickHouse (optional)
* **ML**: Python microservice (FastAPI) or pure-Java inference via ONNX; scikit-learn/Prophet; embedding via OpenAI/Sentence-Transformers
* **Frontend**: Angular 19 + Angular Material (macOS-inspired theme), Nx workspace; Electron/Capacitor for desktop/mobile
* **Auth**: Keycloak
* **DevOps**: GitLab CI, Docker, Helm, K8s, Vault, OpenTelemetry, Prometheus, Grafana, ELK

---

## 11) Service Breakdown (microservices)

1. **Gateway**
2. **Auth (Keycloak)**
3. **Ingestion Svc** (SMS/IMAP/File/Webhook adaptors)
4. **Parsing & Normalization Svc**
5. **Enrichment Svc** (merchant resolver, FX)
6. **Classification Svc** (rules + ML)
7. **Subscriptions Svc**
8. **Anomaly Svc**
9. **Forecasting Svc**
10. **Nudges Svc** (policy DSL, experiments)
11. **Ledger API** (transactions/budgets/goals)
12. **Document Svc** (receipts/statements → OCR)
13. **Copilot Orchestrator** (Spring AI + tools)
14. **Reporting/Exports Svc**

> Start MVP with **modular monolith** (separate packages, shared DB) then carve out services along Kafka topics.

---

## 12) Database Design (OLTP)

**Tables (key ones):**

* `users`, `households`, `household_members`
* `accounts` (type: bank/wallet/card; masked number; provider)
* `transactions_raw` (source payload, hash)
* `transactions` (normalized)
* `merchants`, `categories`, `category_rules`
* `subscriptions` (pattern, interval, last\_seen, next\_due)
* `bills` (amount, cycle, reminders)
* `budgets` (period, envelope\_mode, rollover)
* `goals` (target\_amount, deadline, contributions)
* `alerts`, `nudges`
* `documents` (s3\_url, ocr\_text, checksum)
* `feedback_labels`

**Partitioning:** monthly on `transactions` by `txn_ts`; indexes on `(user_id, txn_ts)`, `(merchant_id, category_id)`.

---

## 13) Analytics & RAG

* **Star schema** in warehouse: `fact_transactions`, `dim_date`, `dim_merchant`, `dim_category`.
* **RAG memory**: store embeddings for merchant texts, OCR lines, and user Q\&A summaries in `pgvector`.
* **Copilot grounding**: SQL-to-text layer with canned measures (spend by month, category drift, subscription deltas).

---

## 14) Observability & Quality Gates

* Golden signals dashboard (latency, errors, Kafka lag, queue depths, OCR throughput).
* Data quality checks (Great Expectations/dbt tests): missing categories, orphan merchants, abnormal spikes.
* Model monitoring: drift in category distribution; feedback acceptance rate.

---

## 15) Security Playbook

* Threat model per component; rotate keys; mTLS between services.
* Secrets in Vault; short-lived presigned S3 URLs only.
* PII map & Data retention policy; right-to-be-forgotten job.

---

## 16) Build Order & Scoping (Do First, One Slice at a Time)

**Rule:** Each step must ship a vertical slice (backend → UI) that a user can touch. No parallel half-built systems.

### Step 0 — Foundations (Days 1–3)

**Goal:** Run the stack locally with auth.

* Docker Compose: Postgres, Keycloak, MinIO, Kafka, Prometheus
* Spring Boot app-api (health check), Angular shell app (login screen)
  **DoD:** `docker compose up` → login works (Keycloak), healthchecks green.

### Step 1 — Ingestion (Manual & CSV) (Days 4–7)

**Goal:** Get real data in quickly without complex adapters.

* API: `POST /v1/ingest/csv` (single schema) + manual transaction entry UI
* DB: `transactions_raw`, `transactions`
* UI: Upload CSV → see rows in Transactions table
  **DoD:** Import 1k+ rows in < 60s; idempotent re-import.

### Step 2 — Normalization & Ledger Read (Days 8–12)

**Goal:** Clean, consistent ledger powering the UI.

* Normalize pipeline (sync) → write `transactions`
* Query API: `/v1/transactions?from&to&filters`
* UI: paginated table + quick filters (date, amount, direction)
  **DoD:** P95 read < 300ms on 50k rows; filters correct.

### Step 3 — Categorization (Rules-First) (Days 13–18)

**Goal:** Useful categories without ML.

* Rules engine (regex/templates per merchant/text)
* Feedback capture: user can change category
* UI: bulk edit; category color chips
  **DoD:** ≥85% precision on seed dataset; feedback persisted.

### Step 4 — Budgets (MVP) (Days 19–24)

**Goal:** Users set limits and see progress.

* CRUD budgets; monthly utilization
* Alerts (server-side) when >80% / >100%
* UI: budget rings + list
  **DoD:** Utilization matches SQL checks; two alerts delivered in demo.

### Step 5 — Subscriptions & Bills (Days 25–30)

**Goal:** Detect recurring payments.

* Pattern mining on categorized ledger (day-of-month clustering)
* Entities: `subscriptions`, `bills`
* UI: calendar view + subscription list
  **DoD:** Detects known Netflix/Spotify test cases; next due date visible.

### Step 6 — Anomaly Signals (Days 31–35)

**Goal:** Simple but valuable safety.

* Duplicate/spike detection (rolling z-score + rules)
* Alerts in `/v1/insights`
* UI: Anomalies tab with action chips
  **DoD:** Catches seeded anomalies; false positive rate <10% on test set.

### Step 7 — Copilot v1 (Days 36–42)

**Goal:** Ask basic spend questions.

* Spring AI tool-calling to two tools: `getSpendByCategory`, `getBudgetUtilization`
* Hinglish intent classifier (rules-first)
* UI: side panel chat
  **DoD:** 10 canned queries pass deterministically with citations to API calls.

### Step 8 — Forecasting (Stretch) (Days 43–49)

**Goal:** 60-day cashflow preview.

* Prophet baseline service (batch)
* UI: line chart with confidence band
  **DoD:** Backtests on 6 months with MAPE < 15% (sample users).

---

## 16.1 Scope Kill List (Not in MVP)

* Account Aggregator/Bank scraping
* OCR for PDFs/receipts (placeholder upload only)
* Vector/RAG features
* LSTM/advanced ML; paid OCR/LLM vendors
* Mobile app; Android SMS bridge

## 16.2 Guardrails & Gates

* **Gate A (after Step 2):** Data model stabilized (Flyway v1), no breaking changes without migration plan.
* **Gate B (after Step 4):** Alerts framework in place; privacy review (PII map, field encryption on).
* **Gate C (after Step 7):** Beta readiness: error budgets, on-call, dashboards live.

## 17) Reference UI (macOS-inspired Angular)

* Minimalist, glass/neo toggles; dense tables; quick filters (category, merchant, time, amount range); timeline spend graph; budget rings; subscription calendar; copilot side panel.

---

## 18) Open Questions / Next Decisions

* Bank connector strategy in India (consents/AA vs manual SMS): phase plan?
* Warehouse choice: Stick to Postgres first or add DuckDB/ClickHouse later?
* On-device vs cloud-only mode priorities.
* Which OCR vendor for PDFs (open-source first + pluggable paid fallback)?
* Push notifications provider (FCM vs APNs via backend).

---

## 19) Appendix — Example Contracts

**Kafka topic: `raw.txn` (value JSON)**

```json
{
  "id": "uuid",
  "userId": "uuid",
  "source": "sms|email|file|webhook|manual",
  "payload": { "text": "HDFC: INR 1,250 spent at SWIGGY ..." },
  "ingestedAt": "2025-08-15T12:00:00Z",
  "idempotencyKey": "sha256(...)"
}
```

**REST: POST /v1/transactions/query**

```json
{
  "from": "2025-07-01",
  "to": "2025-08-15",
  "filters": { "category": ["Food"], "minAmount": 200 },
  "groupBy": ["month"],
  "metrics": ["sum", "count"]
}
```

**Copilot tool schema — getBudgetUtilization**

```json
{
  "name": "getBudgetUtilization",
  "params": { "month": "YYYY-MM", "category": "string" },
  "returns": { "spent": "number", "budget": "number", "percent": "number" }
}
```

---

## 20) Build Strategy: Modular Monolith Layout (Backend)

```
trackwise/
  app-api/                # Spring Boot app (controllers, gateway config)
  core-ledger/            # domain: accounts, transactions, budgets, goals
  core-ingestion/         # adapters: sms, email, file, webhooks
  core-pipeline/          # normalization, enrichment, classification
  core-insights/          # subscriptions, anomaly, forecasting, nudges
  core-copilot/           # prompt orchestration, RAG, tools
  shared-kernel/          # common DTOs, events, errors, security, utils
  infra/                  # db migrations (Flyway), Kafka, MinIO clients
```

**Frontend (Nx workspace)**

```
apps/web
libs/ui (macOS theme)
libs/feature-transactions
libs/feature-budgets
libs/feature-subscriptions
libs/feature-copilot
libs/shared/data-access
```

---

## 21) Next Steps (Actionable)

1. Initialize repos & scaffolding (modules above) + GitLab CI skeleton.
2. Provision local dev stack via Docker Compose (Postgres + Kafka + MinIO + Keycloak).
3. Define V1 schemas (Postgres + Kafka contracts) and publish to `shared-kernel`.
4. Implement SMS parser for top 5 Indian banks & UPI; file importer for CSV (standard columns).
5. Ship the Angular dashboard: Transactions table, category editor, budget rings.
6. Wire rules-based categorization + feedback capture.
7. Add recurring/subscription detection and bill calendar.
8. Deliver Copilot v1 with two tools: `getSpendByCategory`, `getBudgetUtilization`.

---

> This plan is intentionally opinionated around Java/Spring Boot + Angular, Kafka, PostgreSQL, and privacy-first design to align with our current strengths and the TrackWise AI vision. We'll iterate and carve out microservices as load and team size grow.
