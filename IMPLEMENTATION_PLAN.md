# Implementation Plan — Portfolio Performance Attribution API

## Overview
Build a single production-leaning `POST /api/performance/attribution` endpoint in Spring Boot 3.2.5 / Java 21 that computes contribution-to-return for asset groups with resilient fallback pricing, graceful degradation, and idempotency.

---

## Architecture Decisions

| Concern | Decision | Reason |
|---|---|---|
| Runtime | Spring Boot 3.2.5, Java 21 | Assignment spec |
| Build | Maven | Standard enterprise tooling |
| Persistence | H2 in-memory (JPA) | Zero-infra idempotency store |
| Financial math | BigDecimal (6dp, HALF_UP) | Avoid IEEE 754 drift |
| Validation | Custom validator + Bean Validation | Weight-sum rule needs cross-field logic |
| Testing | MockMvc + @SpringBootTest | Full-slice tests, no mocking of business logic |
| Fallback model | Nullable returnPct + fallbackReturnPct | Models pricing delay directly in payload |

---

## Business Rules Reference

| # | Rule |
|---|---|
| R1 | Reject `INVALID_INPUT` if total weight < 99% or > 101% |
| R2 | `contributionPct = weightPct × returnPct / 100` |
| R3 | `totalContributionPct = Σ contributionPct` |
| R4 | If `returnPct` null + fallback provided → use fallback, `pricingMode = FALLBACK_USED` |
| R5 | If `returnPct` null + no fallback, 1 group → `status = DEGRADED` |
| R6 | If `returnPct` null + no fallback, 2+ groups → `status = REVIEW_REQUIRED` |
| R7 | Same `requestId` → return cached response (idempotent) |
| R8 | Response always includes `degraded`, `warnings`, per-group `pricingMode` |

---

## Step-by-Step Implementation Phases

### Phase 1 — Project Scaffold
**Goal:** Runnable Spring Boot skeleton with H2, JPA, and Web.

Steps:
1. Create `pom.xml` with dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `h2`, `jackson-databind`.
2. Create `PortfolioAttributionApplication.java` main class.
3. Create `application.properties` with H2 datasource, JPA DDL-auto, and logging config.

Deliverable: `mvn spring-boot:run` starts without errors.

---

### Phase 2 — Request Model
**Goal:** Typed, validated input DTOs.

Steps:
1. Create `GroupInput.java` — fields: `groupName` (NotBlank), `weightPct` (NotNull, positive), `returnPct` (nullable BigDecimal), `fallbackReturnPct` (nullable BigDecimal).
2. Create `AttributionRequest.java` — fields: `requestId` (NotBlank), `portfolioId` (NotBlank), `valuationDate` (NotNull, LocalDate), `groups` (NotEmpty list), `currency` (NotBlank), `requestedBy` (NotBlank).

---

### Phase 3 — Response Model
**Goal:** Typed response DTOs with all resilience metadata.

Steps:
1. Create `PricingMode` enum: `PRIMARY`, `FALLBACK_USED`.
2. Create `AttributionStatus` enum: `VALID`, `DEGRADED`, `REVIEW_REQUIRED`, `INVALID_INPUT`.
3. Create `GroupContribution.java` — fields: `groupName`, `contributionPct` (BigDecimal), `pricingMode`.
4. Create `AttributionResponse.java` — fields: `requestId`, `portfolioId`, `valuationDate`, `totalContributionPct`, `groupContributions`, `status`, `degraded`, `warnings`, `processedAt`.

---

### Phase 4 — Weight Validator
**Goal:** Cross-field validation that weight sum lies in [99, 101].

Steps:
1. Create `AttributionRequestValidator.java` with method `validate(AttributionRequest)` returning `Optional<String>` error message.
2. Sum all `weightPct` values using BigDecimal.
3. Return error string if sum < 99 or sum > 101, else empty.

---

### Phase 5 — Attribution Service Core
**Goal:** Pure business logic — no HTTP, no persistence.

Steps:
1. Create `AttributionService.java` (`@Service`).
2. Inject `AttributionRequestValidator`.
3. Method `AttributionResponse calculate(AttributionRequest)`:
   a. Validate weights → return `INVALID_INPUT` response if failed.
   b. For each group, resolve return: primary → fallback → unresolvable.
   c. Compute `contributionPct = weightPct × effectiveReturn / 100` (BigDecimal).
   d. Count unresolvable groups; set `status` per R5/R6.
   e. Sum `totalContributionPct`.
   f. Build warnings list.
   g. Return `AttributionResponse`.

---

### Phase 6 — Idempotency Store
**Goal:** Persist and retrieve results by `requestId`.

Steps:
1. Create `AttributionResultEntity.java` (`@Entity`) — columns: `requestId` (PK, varchar), `responseJson` (CLOB), `createdAt`.
2. Create `AttributionResultRepository.java` extending `JpaRepository<AttributionResultEntity, String>`.
3. In `AttributionService`, before calculation: check repository for existing `requestId`; if found, deserialize and return cached response.
4. After calculation: serialize response to JSON and persist entity.

---

### Phase 7 — Controller
**Goal:** HTTP layer wiring with correlation logging.

Steps:
1. Create `AttributionController.java` (`@RestController`, `@RequestMapping("/api/performance")`).
2. POST `/attribution` method accepting `@Valid @RequestBody AttributionRequest`.
3. Log `requestId` at INFO on entry and exit.
4. Delegate to `AttributionService.calculate()`.
5. Return `ResponseEntity<AttributionResponse>` with HTTP 200 for all business outcomes (VALID, DEGRADED, REVIEW_REQUIRED) and HTTP 400 for INVALID_INPUT.
6. Add `@ExceptionHandler(MethodArgumentNotValidException)` for Bean Validation failures.

---

### Phase 8 — Tests (≥ 5 scenarios)
**Goal:** Verify all critical paths with MockMvc + @SpringBootTest.

Test cases:
| # | Scenario | Expected |
|---|---|---|
| T1 | All groups have primary return | status=VALID, pricingMode=PRIMARY for all |
| T2 | One group uses fallbackReturnPct | status=VALID, pricingMode=FALLBACK_USED, warning present |
| T3 | One group missing return, no fallback | status=DEGRADED, degraded=true |
| T4 | Two groups missing return, no fallback | status=REVIEW_REQUIRED |
| T5 | Weight sum < 99 | HTTP 400, status=INVALID_INPUT |
| T6 | Repeat same requestId | Identical response returned, no duplicate processing |

---

### Phase 9 — Custom Agent / Reusable Instruction
**Goal:** Demonstrate a reusable quality pattern.

Deliverable: `RESILIENCE_REVIEWER_AGENT.md` — a reusable prompt instruction set that acts as a "Resiliency Reviewer" agent. Applied during code review to check fallback coverage, degraded-path completeness, and idempotency correctness.

---

### Phase 10 — Production Readiness Polish
**Goal:** Logging, error handling, and correlation IDs.

Steps:
1. Add MDC `requestId` on every request for log correlation.
2. Structured error response for 400/500 paths.
3. `@Slf4j` logging at key service decision points (weight validation, fallback used, degraded triggered).
4. Verify H2 console disabled in default profile.

---

## File Checklist

- [ ] `pom.xml`
- [ ] `PortfolioAttributionApplication.java`
- [ ] `application.properties`
- [ ] `GroupInput.java`
- [ ] `AttributionRequest.java`
- [ ] `PricingMode.java` (enum)
- [ ] `AttributionStatus.java` (enum)
- [ ] `GroupContribution.java`
- [ ] `AttributionResponse.java`
- [ ] `AttributionRequestValidator.java`
- [ ] `AttributionService.java`
- [ ] `AttributionResultEntity.java`
- [ ] `AttributionResultRepository.java`
- [ ] `AttributionController.java`
- [ ] `AttributionControllerTest.java`
- [ ] `RESILIENCE_REVIEWER_AGENT.md`
