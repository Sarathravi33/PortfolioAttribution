# GitHub Copilot Chat Prompt Log
## Portfolio Performance Attribution API

Sequential prompts used to implement the service step-by-step. Each prompt builds on the previous. Use these in order inside GitHub Copilot Chat (Ctrl+I or Chat panel).

---

### Prompt 01 — Project Scaffold

```
Create a Spring Boot 3.2.5 Maven project named portfolio-attribution using Java 21.
Add these dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa,
spring-boot-starter-validation, com.h2database:h2 (runtime), and spring-boot-starter-test.
Generate pom.xml and PortfolioAttributionApplication.java main class.
```

**Purpose:** Produces a runnable skeleton. Verify with `mvn spring-boot:run`.

---

### Prompt 02 — application.properties

```
Generate src/main/resources/application.properties for this Spring Boot project with:
- H2 in-memory datasource (url: jdbc:h2:mem:attributiondb)
- spring.jpa.hibernate.ddl-auto=create-drop
- spring.jpa.show-sql=false
- Logging level INFO for com.portfolio
- H2 console disabled
```

**Purpose:** Configures the embedded database and logging.

---

### Prompt 03 — Request DTOs

```
Create two Java record/class files in package com.portfolio.attribution.model.request:

1. GroupInput.java — fields: groupName (String, @NotBlank), weightPct (BigDecimal, @NotNull),
   returnPct (BigDecimal, nullable), fallbackReturnPct (BigDecimal, nullable).

2. AttributionRequest.java — fields: requestId (String, @NotBlank), portfolioId (String, @NotBlank),
   valuationDate (LocalDate, @NotNull), groups (List<GroupInput>, @NotEmpty, @Valid),
   currency (String, @NotBlank), requestedBy (String, @NotBlank).

Use Lombok @Data or Java records. Include Jackson annotations for JSON snake_case if needed.
```

**Purpose:** Typed, validated input model.

---

### Prompt 04 — Response DTOs and Enums

```
In package com.portfolio.attribution.model.response create:

1. Enum PricingMode with values PRIMARY and FALLBACK_USED.
2. Enum AttributionStatus with values VALID, DEGRADED, REVIEW_REQUIRED, INVALID_INPUT.
3. GroupContribution.java — fields: groupName (String), contributionPct (BigDecimal), pricingMode (PricingMode).
4. AttributionResponse.java — fields: requestId, portfolioId, valuationDate (LocalDate),
   totalContributionPct (BigDecimal), groupContributions (List<GroupContribution>),
   status (AttributionStatus), degraded (boolean), warnings (List<String>),
   processedAt (Instant).

Use Lombok @Builder @Data or Java records. Ensure Instant serializes as ISO-8601 UTC.
```

**Purpose:** Complete response model with all resilience metadata fields.

---

### Prompt 05 — Weight Validator

```
Create AttributionRequestValidator.java in package com.portfolio.attribution.validation.

It should have one method:
  Optional<String> validate(AttributionRequest request)

Logic: sum all group weightPct values using BigDecimal. If the sum is less than 99.0 or
greater than 101.0 return Optional.of("Total weight X% is outside the valid range [99%, 101%]").
Otherwise return Optional.empty().

Do not use annotations — this is a plain Spring @Component.
```

**Purpose:** Enforces the cross-field weight rule (Business Rule R1).

---

### Prompt 06 — Attribution Service (Core Logic)

```
Create AttributionService.java in package com.portfolio.attribution.service annotated @Service.

Inject AttributionRequestValidator. Implement:
  AttributionResponse calculate(AttributionRequest request)

Step 1: Validate weights. If invalid, return a response with status=INVALID_INPUT,
  degraded=false, empty groupContributions, totalContributionPct=0, and the error in warnings.

Step 2: For each GroupInput resolve the effective return:
  - If returnPct is not null: use it, pricingMode=PRIMARY
  - If returnPct is null and fallbackReturnPct is not null: use fallbackReturnPct, pricingMode=FALLBACK_USED,
    add warning "Fallback pricing used for {groupName}"
  - If both are null: group is unresolvable — track it separately

Step 3: Count unresolvable groups. Set status:
  - 0 unresolvable → VALID
  - 1 unresolvable → DEGRADED
  - 2+ unresolvable → REVIEW_REQUIRED
  Set degraded = (status != VALID && status != INVALID_INPUT)

Step 4: For resolvable groups compute contributionPct = weightPct.multiply(effectiveReturn)
  .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)

Step 5: totalContributionPct = sum of all contributionPct values (6dp)

Step 6: For unresolvable groups add warning "Return unavailable for {groupName}, group excluded from total"

Return completed AttributionResponse with processedAt = Instant.now().
```

**Purpose:** Central business logic with fallback/degradation/BigDecimal precision.

---

### Prompt 07 — Idempotency Entity

```
In package com.portfolio.attribution.repository create:

1. AttributionResultEntity.java — @Entity @Table(name="attribution_results") with fields:
   requestId (@Id String), responseJson (@Lob String), createdAt (Instant).

2. AttributionResultRepository.java — interface extending JpaRepository<AttributionResultEntity, String>.

No extra methods needed beyond the inherited findById and save.
```

**Purpose:** Persistence layer for idempotency store.

---

### Prompt 08 — Wire Idempotency into Service

```
Update AttributionService to inject AttributionResultRepository and ObjectMapper.

At the start of the calculate() method, before validation:
1. Call repository.findById(request.getRequestId()).
2. If present, deserialize responseJson to AttributionResponse using ObjectMapper and return it immediately.

After computing the response, serialize it to JSON and save a new AttributionResultEntity
with requestId, responseJson, and createdAt=Instant.now(). Then return the response.

Handle JsonProcessingException by wrapping in RuntimeException.
```

**Purpose:** Idempotency — same requestId returns the cached result.

---

### Prompt 09 — Controller

```
Create AttributionController.java in package com.portfolio.attribution.controller.
Annotate @RestController @RequestMapping("/api/performance") @Slf4j.

Add POST /attribution method:
  @PostMapping("/attribution")
  ResponseEntity<AttributionResponse> calculate(@Valid @RequestBody AttributionRequest request)

Steps:
1. Log: log.info("Attribution request received: requestId={}, portfolioId={}", ...)
2. Call attributionService.calculate(request).
3. If response.getStatus() == INVALID_INPUT, return ResponseEntity.badRequest().body(response).
4. Otherwise return ResponseEntity.ok(response).
5. Log: log.info("Attribution response: requestId={}, status={}", ...)

Also add @ExceptionHandler(MethodArgumentNotValidException.class) that returns HTTP 400
with a simple error body containing the validation errors.
```

**Purpose:** HTTP layer with correlation logging and correct status codes.

---

### Prompt 10 — MDC Correlation Logging

```
Add a Spring OncePerRequestFilter in package com.portfolio.attribution.config named
CorrelationFilter.java.

In doFilterInternal:
1. Extract X-Request-ID header if present, otherwise use the requestId from the parsed
   request body if available — or generate a UUID fallback.
2. Put the value into MDC under key "requestId".
3. Call filterChain.doFilter().
4. Clear MDC after the response.

Register it as a @Bean in a WebConfig class or annotate directly with @Component.
```

**Purpose:** Every log line carries requestId for end-to-end tracing.

---

### Prompt 11 — JacksonConfig

```
Create JacksonConfig.java in package com.portfolio.attribution.config annotated @Configuration.

Produce a @Bean ObjectMapper that:
- Serializes LocalDate as "yyyy-MM-dd" (JavaTimeModule, WRITE_DATES_AS_TIMESTAMPS=false)
- Serializes Instant as ISO-8601 UTC string
- Does NOT fail on unknown properties

This ObjectMapper will also be injected into AttributionService for idempotency serialization.
```

**Purpose:** Consistent JSON date handling across request, response, and stored blob.

---

### Prompt 12 — Integration Tests

```
Create AttributionControllerTest.java in src/test/java/com/portfolio/attribution
annotated @SpringBootTest(webEnvironment=RANDOM_PORT) @AutoConfigureMockMvc.

Write 6 @Test methods using MockMvc post("/api/performance/attribution"):

T1 — validAllPrimary: all groups have returnPct, weight sums to 100.
  Assert HTTP 200, status=VALID, all pricingMode=PRIMARY.

T2 — validWithFallback: one group has returnPct=null, fallbackReturnPct provided, weight sums to 100.
  Assert HTTP 200, status=VALID, that group pricingMode=FALLBACK_USED, warnings contains "Fallback".

T3 — degradedOneMissing: one group returnPct=null, no fallback, weight sums to 100.
  Assert HTTP 200, status=DEGRADED, degraded=true.

T4 — reviewRequiredTwoMissing: two groups returnPct=null, no fallback, weight sums to 100.
  Assert HTTP 200, status=REVIEW_REQUIRED.

T5 — invalidWeightTooLow: groups weight sum = 80.
  Assert HTTP 400, status=INVALID_INPUT.

T6 — idempotentRepeatRequest: send same requestId twice. 
  Assert both responses are identical (same processedAt timestamp).
```

**Purpose:** Full-slice tests covering all business paths.

---

### Prompt 13 — Resiliency Self-Review

```
Review AttributionService.java against these resiliency criteria and report any gaps:

1. Fallback path: is fallbackReturnPct used when and only when returnPct is null?
2. Degraded path: is status correctly set to DEGRADED for exactly 1 missing group and
   REVIEW_REQUIRED for 2+ missing groups?
3. Idempotency: does a repeat requestId always return the exact same response (including processedAt)?
4. BigDecimal precision: are all contribution calculations using BigDecimal with explicit scale and rounding?
5. Weight tolerance: is the [99%, 101%] check applied before any calculation?
6. Warnings completeness: is every fallback usage and every excluded group captured in warnings?

List any gap found and suggest a fix.
```

**Purpose:** Resiliency Reviewer pass — catches logical gaps before final submission.

---

### Prompt 14 — Final Polish & README Verification

```
Review the complete project for:
1. Any @SuppressWarnings or TODOs left in production code.
2. Unused imports.
3. Confirm H2 console is disabled in application.properties.
4. Confirm BigDecimal scale is 6 with HALF_UP everywhere.
5. Confirm all test assertions use jsonPath() for specific field checks, not just HTTP status.

Also verify README.md contains: setup steps, API example, status table, and assumptions section.
Report any issues found.
```

**Purpose:** Final quality gate before submission.

---

## Prompt Usage Pattern

These prompts follow a **scaffold → model → logic → persistence → controller → tests → review** order that mirrors a production feature development cycle. Each prompt is self-contained enough to be pasted directly into Copilot Chat, and references prior artifacts by class name so Copilot can resolve context from open editor tabs.

The **Resiliency Reviewer** (Prompt 13) is the custom agent pattern — a structured checklist instruction that forces systematic review of all fallback/degradation/idempotency paths rather than relying on ad-hoc reading.
