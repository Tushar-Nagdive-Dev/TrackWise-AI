*Last updated: 15 Aug 2025 (IST)*

---

# 1) Executive Summary

This document defines the architecture for TrackWise AI using **two independent Spring Boot microservices**, each owning its **own PostgreSQL database**:

* **App API (Ledger Service)** — Core product + finance domain. DB: `trackwise_app`.
* **AI API (Intelligence Service)** — AI-focused endpoints & metadata. DB: `trackwise_ai`.

Services communicate via REST (App → AI). Later, Kafka can be introduced for async signals (training/audit). Security is OIDC (Keycloak) for user traffic and OAuth2 client-credentials for service-to-service in non-dev environments.

---

# 2) System Context

```
[User (Web/Electron)] --(OIDC/JWT)--> [API Gateway*] --> [App API]
                                               |                
                                               └----REST------> [AI API]
                                                               (OAuth2 S2S)

Data Stores:
- App API → PostgreSQL (trackwise_app) [Authoritative Ledger]
- AI  API → PostgreSQL (trackwise_ai)  [Models/Prompts/Embeddings/Logs]
- (Optional) MinIO/S3 for documents/exports
- (Optional) Redis for caching feature flags/embedding cache
```

\*Gateway optional for MVP; can start with direct service ingress.

---

# 3) Service Responsibilities & Boundaries

## 3.1 App API (Ledger Service)

**Owns:** Users, Accounts, Transactions, Categories, Budgets, Subscriptions, Insights.
**Exposes:**

* `/v1/transactions` — query, import CSV, patch category
* `/v1/categories` — list/rules (rules-first)
* `/v1/budgets` — CRUD & utilization
* `/v1/insights` — anomalies, nudges
  **Integrates with AI API:** categorization suggestions, intent detection (copilot preflight).
  **Never writes to AI DB.**

## 3.2 AI API (Intelligence Service)

**Owns:** Prompt templates, model registry, inference logs, embedding cache.
**Exposes:**

* `/v1/ai/categorize` — suggests categories (rules + optional LLM)
* `/v1/ai/intent` — Hinglish/English intent + entities
* `/v1/ai/embeddings` — vectorization endpoint (optional)
* `/v1/ai/chat` — orchestrated responses (later)
  **Never writes to App DB.**

---

# 4) Data Ownership & Schemas

## 4.1 App Database (`trackwise_app`) — Flyway V1

* `users(id, email, created_at)`
* `accounts(id, user_id→users, provider, masked_number, created_at)`
* `categories(id, parent_id→categories, name, code)`
* `transactions(id, user_id, account_id, txn_ts, amount, currency, direction, merchant_text, category_id?, tags[], confidence, source, raw_hash UNIQUE, created_at)`
  **Indexes:** `(user_id, txn_ts)`, `(user_id, category_id)`
  **Partitioning (V2):** monthly on `transactions(txn_ts)` via `pg_partman` (optional)

## 4.2 AI Database (`trackwise_ai`) — Flyway V1

* `model_registry(id, name, provider, version, status, created_at)`
* `prompt_versions(id, task, version, template, created_at)`
* `embedding_cache(id, key UNIQUE, vector REAL[], meta JSONB, created_at)`
* `inference_logs(id, task, request JSONB, response JSONB, latency_ms, model_id→model_registry, created_at)`
  **Indexes:** `(task, created_at)`
  **PII Policy:** Store minimal text; hash/mask merchant or user hints when feasible.

---

# 5) API Contracts (Selected)

## App → AI: Categorization

**POST** `/v1/ai/categorize`

```json
{ "text":"HDFC: INR 1,250 at SWIGGY", "merchant":"SWIGGY", "amount":1250, "currency":"INR", "direction":"debit", "hints":["food"], "top_k":3 }
```

**200**

```json
{ "suggestions":[ {"category":"Food & Dining","confidence":0.91} ], "explanation":"matched merchant + lexical cues" }
```

## App: Transactions Query

**GET** `/v1/transactions?from=2025-07-01&to=2025-08-15&direction=debit`
**200** `{ "data":[...], "page":{...} }`

## App: Budgets Utilization

**GET** `/v1/budgets/utilization?month=2025-08`
**200** `{ "category":"Food & Dining", "spent": 8420.50, "budget": 10000, "percent": 84.2 }`

---

# 6) Security Architecture

* **User Auth (north-south):** Keycloak OIDC → JWT access tokens; RBAC roles: USER, FAMILY\_ADMIN.
* **Service Auth (east-west):** Dev → header `X-Service-Token`; Non-dev → **OAuth2 client\_credentials** (Keycloak), audience scoping.
* **Transport:** TLS everywhere; mTLS between services (non-dev optional).
* **Data at Rest:** AES-256 (disk); `pgcrypto` column-level for PII.
* **Secrets:** Vault/K8s secrets; no secrets in images.
* **Audit Logs:** Immutable index; request IDs propagated across services.

---

# 7) Observability & SLOs

* **Tracing:** OpenTelemetry (HTTP spans: user → App → AI → DB)
* **Metrics:** Prometheus (HTTP latency, DB timings, error rate). Golden SLOs:

    * App read P95 < **300 ms** on 50k txns/user
    * App→AI round-trip P95 < **800 ms** for categorize/intent
    * Uptime 99.9% (prod)
* **Logging:** JSON structured; correlation IDs; sensitive fields redacted.
* **Dashboards:** Grafana (latency, throughput, error budgets); log search in ELK/Opensearch.

---

# 8) Deployment Model

**Local:** Docker Compose (one Postgres instance, two DBs).
**Dev/QA/Prod:** Kubernetes + Helm; per-service Deployments, Services, HPAs; Postgres as managed service(s).

**Ingress Options:**

* Single API Gateway (Spring Cloud Gateway/NGINX) routing to `app-api` and `ai-api`.
* Or two public ingresses with separate hostnames.

**DB Options:**

* Dev: single Postgres with two DBs
* Prod Preferred: **two managed Postgres clusters** (isolation, backups, DR windows)

**Images & Tags:**

* `app-api:<semver>` and `ai-api:<semver>`; CI generates SBOMs and signs images.

---

# 9) CI/CD Strategy

* **Path-filtered pipelines**: build/test/deploy only changed service(s).
* **DB migrations:** Flyway invoked on startup; also run in CI to fail fast.
* **Contract tests:** Consumer-provider tests between App and AI; OpenAPI drift check.
* **Quality gates:** Unit + integration tests; static analysis; dependency scanning; IaC checks.
* **Environments:** Review apps (preview namespace) on MRs; blue-green or canary to prod.

---

# 10) Error Handling & Resilience

* **App→AI:** RestClient with retries (exponential backoff), circuit breaker (Resilience4j), 2s connect / 10s read timeouts.
* **Fallbacks:** On AI failure, App continues with rules-only categorization (lower confidence + UI hint).
* **Idempotency:** `raw_hash` on transactions; dedupe on import.
* **Rate limits:** per user (gateway) + per endpoint.

---

# 11) Build Order (Vertical Slices)

1. **Foundations:** Compose up; health; DB migrations; OTel wiring.
2. **CSV Import + Transactions Query (App)**: upload → list → filters.
3. **Categorization (AI stub + App integration):** App calls AI; patch category.
4. **Budgets (App):** CRUD + utilization + alerts.
5. **Intent (AI):** Hinglish rules; App copilot preflight.
6. **Subscriptions/Anomalies (App):** insights endpoint.
7. **Spring AI (AI):** replace stub with model-backed provider.

---

# 12) Risks & Mitigations

* **Coupling via DTO drift:** Use shared-kernel + contract tests; pin OpenAPI.
* **AI latency/cost:** Cache suggestions; batch embeddings; keep rules-first fallback.
* **Schema churn:** Gate changes with Flyway versioning + data migration scripts.
* **Security gaps in dev:** Use service token only locally; enforce OAuth2 in non-dev; secrets via Vault.

---

# 13) Appendix — Config & Samples

## 13.1 Docker Compose (dev)

```yaml
version: "3.9"
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
  db-init:
    image: postgres:16
    depends_on: [postgres]
    entrypoint: ["bash","-lc","psql postgresql://postgres:postgres@postgres:5432 -c 'CREATE DATABASE trackwise_app;' -c 'CREATE DATABASE trackwise_ai;' && echo done"]
  app-api:
    build: ../services/app-api
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/trackwise_app
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      AI_BASE_URL: http://ai-api:8081
      SERVICE_TOKEN: dev-secret
    depends_on: [postgres, db-init]
    ports: ["8080:8080"]
  ai-api:
    build: ../services/ai-api
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/trackwise_ai
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SERVICE_TOKEN: dev-secret
      SPRING_AI_OPENAI_API_KEY: ""
    depends_on: [postgres, db-init]
    ports: ["8081:8081"]
volumes: { pgdata: {} }
```

## 13.2 Minimal App→AI Client (Java)

```java
@Component
@RequiredArgsConstructor
public class AiClient {
  @Value("${ai.base-url}") String baseUrl;
  @Value("${security.service-token}") String token;
  private final RestClient rest = RestClient.create();

  public CategorySuggestionResponse suggest(CategorySuggestionRequest req){
    return rest.post().uri(baseUrl + "/v1/ai/categorize")
      .header("X-Service-Token", token)
      .body(req)
      .retrieve()
      .body(CategorySuggestionResponse.class);
  }
}
```

## 13.3 AI Categorize (Rules-first)

```java
@Service
public class CategorizeService {
  public CategorySuggestionResponse suggest(CategorySuggestionRequest req){
    var text = Optional.ofNullable(req.text()).orElse("").toLowerCase();
    var cat = (text.contains("swiggy") || text.contains("zomato")) ? "Food & Dining" : "Uncategorized";
    return new CategorySuggestionResponse(List.of(new Suggestion(cat, 0.75)), "rule-based stub");
  }
}
```

---

# 14) Monorepo vs Polyrepo Notes (for later)

**Monorepo now** → faster atomic changes, shared contracts, single dev loop.
**Polyrepo later** → if teams and release cadences diverge; migrate with `git subtree`/`filter-repo`.
Guardrails: path-filtered CI, CODEOWNERS, per-service versioning and deployments.
