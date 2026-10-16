# NOTES.md

## 1. What was changed and why

### Per-User Redemption Limit (Core Task)
- **Database Schema (`schema.sql`)**:
  - Added `per_user_limit INT` to the `campaign` table so each campaign can configure an optional limit.
- **Configuration (`application.yml`)**:
  - Added `campaign.default-per-user-limit: 2` to supply the required default fallback limit of 2 when a campaign has no explicit limit configured.
- **Model (`Campaign.java`)**:
  - Added `perUserLimit` field with getters and setters to expose the campaign limit.
- **Repository (`RedemptionRepository.java`)**:
  - Added `countByCampaignIdAndUserId(Long campaignId, String userId)` to query existing redemptions per user per campaign.
- **Service (`VoucherService.java`)**:
  - Added limit check: if user redemptions >= campaign per-user limit (or default 2), reject with `RedeemResponse.fail("User redemption limit reached")`.

### Concurrency & Race Condition Elimination
- **Atomic Voucher Claim (`VoucherRepository.java`)**:
  - Added atomic conditional query `@Modifying @Query("UPDATE Voucher v SET v.status = 'REDEEMED', v.redeemedBy = :userId, v.redeemedAt = :redeemedAt WHERE v.code = :code AND v.status = 'ACTIVE'") int claimVoucher(...)`.
  - Replaced in-memory voucher status mutation with DB return count check to eliminate double-claim races on the same voucher code.
- **Atomic Campaign Stock Decrement (`CampaignRepository.java`)**:
  - Added atomic conditional query `@Modifying @Query("UPDATE Campaign c SET c.remainingStock = c.remainingStock - 1 WHERE c.id = :id AND c.remainingStock > 0") int decrementStock(@Param("id") Long id)`.
  - Replaced in-memory stock read-decrement-save in `VoucherService` with DB decrement. If return count is 0, marks transaction rollback and returns `RedeemResponse.fail("Campaign out of stock")`, eliminating lost updates and overselling.
- **Pessimistic Locking on Campaign (`CampaignRepository.java`)**:
  - Added `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT c FROM Campaign c WHERE c.id = :id") Optional<Campaign> findByIdForUpdate(@Param("id") Long id)`.
  - In `VoucherService`, locks the campaign row during redemption to serialize concurrent redemption attempts for that campaign, preventing check-then-act races on per-user limits.
- **Transaction Boundary**:
  - Added `@Transactional` to `VoucherService.redeem()` ensuring all database operations (claim, stock decrement, redemption insert) commit or roll back atomically.

### Database Constraints & Schema Hardening (`schema.sql`)
- Added `UNIQUE (code)` constraint on `voucher` table to guarantee uniqueness at the database level.
- Added foreign key constraints:
  - `voucher(campaign_id) REFERENCES campaign(id)`
  - `redemption(voucher_id) REFERENCES voucher(id)`
  - `redemption(campaign_id) REFERENCES campaign(id)`
  to prevent orphaned records and enforce referential integrity.

### Performance & N+1 Query Elimination
- **Repository (`RedemptionRepository.java`)**:
  - Added `long countByCampaignId(Long campaignId)` aggregate query.
- **Service (`CampaignStatsService.java`)**:
  - Replaced loop querying `redemptionRepository.findByVoucherId(v.getId())` per voucher with direct database aggregate `countByCampaignId(campaignId)`, eliminating N+1 database roundtrips.

### Resilience, Configuration & Observability
- **Audit Client Resilience (`AuditClient.java`)**:
  - Configured 500ms connection and 500ms read timeouts via `RestTemplateBuilder` to prevent HTTP thread and connection pool starvation when audit service is slow.
- **Configuration Externalization (`application.yml`)**:
  - Extracted hardcoded audit URL and API key into `audit.url` and `audit.api-key`, injected via `@Value`.
- **Defensive Error Handling**:
  - Handled missing campaigns gracefully using `.orElse(null)`: throws `404 NOT_FOUND` via `ResponseStatusException` in `CampaignStatsService` and returns `RedeemResponse.fail("Campaign not found")` in `VoucherService`, avoiding unhandled `NoSuchElementException`.
- **Logging**:
  - Updated audit error logging in `VoucherService` to log voucher code and exception context: `log.error("audit call failed for voucher {}", voucher.getCode(), e)`.

### Developer Tooling & Tests
- **Containerization**:
  - Added multi-stage `Containerfile` and `.dockerignore` for rootless Podman/Docker build and test execution.
- **API Collection**:
  - Added Bruno API collection (`bruno/`) covering redeem, void, and campaign stats endpoints.
- **Test Suite**:
  - Added comprehensive unit, integration, and multi-threaded concurrency tests (`CountDownLatch`, `ExecutorService`) covering voucher double-claims, stock overselling, per-user limit race conditions, database integrity constraints, audit timeouts, and N+1 query elimination.

---

## 2. Anything noticed but deliberately NOT changed (and reasoning)

### Synchronous Audit Client Call in Transaction
- **Observation**:
  `auditClient.recordRedemption` is executed synchronously via `RestTemplate` within the `@Transactional` boundary in `VoucherService.redeem()`.
- **Reasoning**:
  Although 500ms connect/read timeouts and try-catch error handling were added to bound failure impact, holding database transactions open during external HTTP calls still risks connection pool exhaustion under extreme traffic. Fixing this properly warrants an asynchronous Transactional Outbox pattern or messaging queue (Kafka/RabbitMQ), which is out of scope for a 3–4 hour exercise.

### In-Memory Non-Transactional `voidVoucher` Flow
- **Observation**:
  `VoucherService.voidVoucher` mutates voucher status in memory and calls `save()` without `@Transactional`, locking, or checking if the voucher is already `REDEEMED`.
- **Reasoning**:
  The brief specifically focused on the redemption flow and per-user limit enforcement. Updating voiding semantics involves domain decisions regarding stock restoration and redemption invalidation.

### In-Memory H2 Database
- **Observation**:
  The application relies on an in-memory H2 database rather than a persistent production RDBMS with migration tooling.
- **Reasoning**:
  Kept as-is to preserve standalone out-of-the-box build and test reproducibility without requiring external database dependencies.

### Non-Standardized HTTP Error Statuses
- **Observation**:
  `VoucherController` returns HTTP 200 with `{ "result": "FAILED", "message": "..." }` for business rejections, while `CampaignController` returns domain DTOs or throws Spring `ResponseStatusException(404)`.
- **Reasoning**:
  Refactoring controller error responses to standard RFC 7807 problem details or uniform HTTP status codes (400/404/409) would alter API contracts for existing clients.

### Campaign `remaining_stock` Initialized by User Input Instead of Voucher Count
- **Observation**:
  `campaign.remaining_stock` (and `total_stock`) is manually initialized via input/seed data rather than dynamically derived from the actual count of vouchers in the `voucher` table. For example, seed data initializes Campaign 1 with stock of 100 even though only 6 voucher rows exist in `data.sql` (with 2 already redeemed or void).
- **Reasoning**:
  - **Performance**: Pre-aggregated stock columns allow high-throughput atomic conditional decrements (`decrementStock`) without executing expensive aggregate table scans (`COUNT(*)`) across millions of voucher records on every redemption.
  - **Voucher Provisioning Lifecycle**: In promotional systems, vouchers may be generated on-demand up to `total_stock` or batch-imported asynchronously. Forcing `remaining_stock` to strictly equal the count of active voucher rows requires redesigning voucher inventory provisioning, which is out of scope for adding per-user limits.

---

## 3. What would break if run on 3 instances behind a load balancer?

1. **In-Memory H2 Database Partitioning**:
   - Because H2 runs in-memory within the JVM (`jdbc:h2:mem`), each of the 3 instances would maintain its own isolated database state. A voucher redeemed or stock decremented on Instance 1 would remain active and unredeemed on Instances 2 and 3, causing immediate duplicate redemptions, incorrect limit enforcement, and desynchronized stats.
2. **Database Lock Contention on Campaign Rows**:
   - Even with a shared external database, `findByIdForUpdate` places a pessimistic write lock on the `campaign` table row. Under high concurrent traffic across 3 instances, all redemptions for the same campaign serialize against that single database row, creating thread contention, increased latency, and database connection pool exhaustion.
3. **Audit Service Latency Cascades**:
   - Synchronous HTTP calls to the external audit service inside `@Transactional` can saturate the database connection pool across all instances if the audit service slows down or experiences network degradation.

---

## 4. What to do next with another day

1. **Transactional Outbox for Audit Logs**:
   - Decouple audit recording from request transactions using an outbox table and asynchronous worker or CDC (Change Data Capture / Debezium) streaming to Kafka/RabbitMQ.
2. **Finer-Grained Concurrency Control for Limits**:
   - Replace the campaign-level pessimistic lock with finer-grained locking (e.g., locking on user + campaign, inserting redemption reservation slots, or using Redis distributed atomic counters) to maximize horizontal throughput without bottle-necking on the campaign row.
3. **Database Migrations & Containerized PostgreSQL**:
   - Introduce Flyway or Liquibase migrations and replace in-memory H2 with PostgreSQL, including HikariCP connection pool tuning.
4. **API Idempotency & Standardized Error Envelopes**:
   - Support `Idempotency-Key` headers on redemption requests to protect against network retries, and standardize error handling with `@ControllerAdvice` and RFC 7807 problem details.
5. **Observability & Metrics**:
   - Add Micrometer metrics (Prometheus scraping for redemption latency, stock levels, limit rejections, audit error counts) and OpenTelemetry distributed tracing.
6. **Hardened Voucher Voiding**:
   - Make `voidVoucher` transactional with conditional state transitions and define stock replenishment rules.

---

## 5. Reflection Answers

- **What did you get wrong first, and how did you notice?**:
  - **Hardcoding default limit in DB schema**: The AI initially added the default `per_user_limit` hardcoded directly into the database schema (`DEFAULT 2` in `schema.sql`). From production experience, default business limits frequently change without warranting database migrations or DDL cutovers. Caught this design flaw and moved the default to dynamic environment/application configuration (`campaign.default-per-user-limit: 2` in `application.yml` via `@Value`), keeping the database column nullable to allow explicit per-campaign overrides.
  - **In-memory state checks & concurrency races**: Initially relied on in-memory status checks (`voucher.getStatus().equals("REDEEMED")`) and `voucherRepository.save(voucher)`. Multi-threaded testing with `CountDownLatch` revealed threads could race through active checks and double-redeem vouchers. Fixed this using atomic database conditional queries (`claimVoucher`, `decrementStock`) and pessimistic row locking (`findByIdForUpdate`).
- **Which AI suggestion did you reject, and why?**:
  - Rejected introducing a Redis distributed lock / Redisson framework. It adds unnecessary operational complexity and external infrastructure dependencies for a focused coding exercise where relational database constraints, atomic conditional queries, and row-level locking suffice.
- **What took you longest?**:
  - Designing and verifying thread synchronization in the concurrent test suite within the containerized test environment.
