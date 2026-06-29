# Portfolio Performance Attribution API

A Spring Boot 3.2.5 / Java 21 service that calculates **contribution-to-return** for asset groups with resilient fallback pricing and graceful degradation when pricing data is unavailable.

---

## Setup & Run

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |

### Build

```bash
mvn clean package -DskipTests
```

### Run

```bash
java -jar target/portfolio-attribution-0.0.1-SNAPSHOT.jar
```

Or directly with Maven:

```bash
mvn spring-boot:run
```

The service starts on **http://localhost:8081**

### Run Tests

```bash
mvn test
```

Expected output: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`

---

## API Contract

### POST `/api/performance/attribution`

#### Request

```json
{
  "requestId":    "ATTR-2001",
  "portfolioId":  "PF-2201",
  "valuationDate":"2026-06-14",
  "currency":     "USD",
  "requestedBy":  "advisor02",
  "groups": [
    { "groupName": "Equity",      "weightPct": 60, "returnPct": 1.5 },
    { "groupName": "FixedIncome", "weightPct": 30, "returnPct": 0.4 },
    { "groupName": "Cash",        "weightPct": 10, "returnPct": null, "fallbackReturnPct": 0.05 }
  ]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `requestId` | String | Yes | Idempotency key — repeated calls return the same response |
| `portfolioId` | String | Yes | Portfolio identifier |
| `valuationDate` | LocalDate | Yes | Format: `yyyy-MM-dd` |
| `groups` | Array | Yes | At least one group required |
| `groups[].groupName` | String | Yes | Asset group label |
| `groups[].weightPct` | BigDecimal | Yes | Must be positive; all groups must sum to 99–101 |
| `groups[].returnPct` | BigDecimal | No | Null triggers fallback or degraded logic |
| `groups[].fallbackReturnPct` | BigDecimal | No | Used only when `returnPct` is null |
| `currency` | String | Yes | e.g. `USD` |
| `requestedBy` | String | Yes | Audit identifier |

#### Response — VALID with fallback

```json
{
  "requestId":           "ATTR-2001",
  "portfolioId":         "PF-2201",
  "valuationDate":       "2026-06-14",
  "totalContributionPct": 1.025000,
  "groupContributions": [
    { "groupName": "Equity",      "contributionPct": 0.900000, "pricingMode": "PRIMARY" },
    { "groupName": "FixedIncome", "contributionPct": 0.120000, "pricingMode": "PRIMARY" },
    { "groupName": "Cash",        "contributionPct": 0.005000, "pricingMode": "FALLBACK_USED" }
  ],
  "status":      "VALID",
  "degraded":    false,
  "warnings":    ["Fallback pricing used for Cash"],
  "processedAt": "2026-06-14T10:45:00Z"
}
```

#### Status Values

| Status | HTTP | Condition |
|---|---|---|
| `VALID` | 200 | All groups resolved — via primary or fallback pricing |
| `DEGRADED` | 200 | Exactly one group has no return and no fallback; partial total returned |
| `REVIEW_REQUIRED` | 200 | Two or more groups have no return and no fallback |
| `INVALID_INPUT` | 400 | Weight sum outside [99%, 101%], or missing required fields |

#### Pricing Mode per Group

| `pricingMode` | Meaning |
|---|---|
| `PRIMARY` | `returnPct` was present and used |
| `FALLBACK_USED` | `returnPct` was null; `fallbackReturnPct` was used instead |

---

## Resilience Behaviour

### Fallback Logic
1. `returnPct` present → use it (`PRIMARY`)
2. `returnPct` null + `fallbackReturnPct` present → use fallback (`FALLBACK_USED`), add warning
3. Both null → group is unresolvable; excluded from total, warning added

### Degradation Ladder

```
0 unresolvable groups  →  VALID
1 unresolvable group   →  DEGRADED   (degraded=true, partial total)
2+ unresolvable groups →  REVIEW_REQUIRED  (degraded=true, partial total)
```

Degraded responses still return contributions from resolvable groups — a partial result is more actionable for an advisor than a hard failure.

### Idempotency
Sending the same `requestId` twice returns the **exact same response**, including the original `processedAt` timestamp. Results are persisted to an in-memory H2 store keyed on `requestId`.

---

## Assumptions

- **Weight tolerance [99%, 101%]** — allows minor rounding from upstream systems while catching obviously wrong inputs. The check uses `BigDecimal` comparison, not floating-point.
- **Contribution formula** — `contributionPct = weightPct × returnPct / 100`, computed with `BigDecimal` at 6 decimal places (`HALF_UP`) to avoid IEEE 754 drift.
- **Degraded partial total** — when groups are excluded, `totalContributionPct` reflects only resolvable groups. The `warnings` list identifies which groups were excluded.
- **No external pricing service** — pricing unavailability is modelled via nullable `returnPct` in the request payload, which is the clearest way to test fallback paths without mocking an HTTP client.
- **H2 in-memory store** — keeps the service self-contained and runnable with no external infrastructure. A production deployment would replace this with Redis or a persistent DB.
- **`processedAt` is frozen on first call** — idempotent repeat calls return the timestamp from the original processing, not the time of the repeat call. This is the correct semantic for idempotency keys.

---

## Project Structure

```
src/
├── main/java/com/portfolio/attribution/
│   ├── controller/
│   │   └── AttributionController.java       POST /api/performance/attribution
│   ├── service/
│   │   └── AttributionService.java          Core logic: fallback, degradation, idempotency
│   ├── validation/
│   │   └── AttributionRequestValidator.java Weight sum [99–101%] cross-field check
│   ├── model/
│   │   ├── request/
│   │   │   ├── AttributionRequest.java
│   │   │   └── GroupInput.java
│   │   └── response/
│   │       ├── AttributionResponse.java
│   │       ├── GroupContribution.java
│   │       ├── AttributionStatus.java       Enum: VALID / DEGRADED / REVIEW_REQUIRED / INVALID_INPUT
│   │       └── PricingMode.java             Enum: PRIMARY / FALLBACK_USED
│   ├── repository/
│   │   ├── AttributionResultEntity.java     JPA entity (H2 idempotency store)
│   │   └── AttributionResultRepository.java
│   └── config/
│       └── JacksonConfig.java               ISO-8601 date serialization
└── test/java/com/portfolio/attribution/
    └── AttributionControllerTest.java       6 MockMvc integration tests
```

---

## Prompt Log & Custom Agent

- **[COPILOT_PROMPT_LOG.md](COPILOT_PROMPT_LOG.md)** — 14 sequential GitHub Copilot Chat prompts used to scaffold and implement the service step-by-step.
- **[RESILIENCE_REVIEWER_AGENT.md](RESILIENCE_REVIEWER_AGENT.md)** — reusable 6-dimension resiliency reviewer agent prompt. Covers fallback coverage, degraded path correctness, idempotency, financial precision, weight validation gate, and warning completeness. Paste into any LLM-based code review tool to audit fallback-heavy services.
- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)** — 10-phase implementation roadmap with architecture decisions and business rules reference.
