# platform:persistence

Durable building blocks shared by services: a JDBC **outbox** repository, a JDBC **idempotency**
(dedupe) store, and the **platform migrations** that create their tables once. Plain `java.sql`
only — no Spring/JPA imports — so it works anywhere a `javax.sql.DataSource` exists (PostgreSQL in
runtime, H2 in PostgreSQL mode in tests). See [ADR-0021](../../docs/adr/0021-persistence-and-transactional-outbox.md).

## API

- `JdbcOutboxRepository(DataSource)` / `(DataSource, maxAttempts)` — implements `OutboxRepository`
  (`save`, `pollPending`, `markDispatched`, `findById`, `countPending`) over `outbox_record`.
  `save` honors `UNIQUE(dedupe_key)`: a duplicate returns `false` (silent no-op), never throws.
- `JdbcDedupeStore(DataSource, Duration ttl)` / `(…, LongSupplier clock)` — implements `DedupeStore`
  (`claim`, `recordOutcome`, `release`, `isProcessed`, `resultRef`) over `idempotency_key`.
  Claim is atomic (expire-then-insert; `23505` → `false`).

Both detect duplicates via SQLState `23505`, the unique-violation code shared by PostgreSQL and H2
(PostgreSQL mode).

## Migrations

Shipped as classpath resources under `io/logisticsos/persistence/migration/`, in the **reserved
version range V900+** so service-owned migrations (`V1..`) never collide:

| File | Table |
|------|-------|
| `V900__outbox.sql` | `outbox_record` (+ `UNIQUE(dedupe_key)`, pending index) |
| `V901__idempotency_keys.sql` | `idempotency_key` (+ expiry index) |

A service using the outbox/idempotency tables points Flyway at both locations:

```properties
spring.flyway.locations=classpath:db/migration,classpath:io/logisticsos/persistence/migration
```

## Tests

`JdbcOutboxRepositoryTest` and `JdbcDedupeStoreTest` run against H2
(`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`) and bootstrap the real migration files via
`SchemaSupport`, so the shipped SQL is exercised. Optional Testcontainers-PostgreSQL ITs raise
dialect fidelity in CI.
