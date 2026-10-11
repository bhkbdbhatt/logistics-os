-- Shared platform migration (ADR-0021). Consumer-side idempotency keys with TTL.
-- Reserved version range V900+ so service-owned migrations (V1..) never collide with platform tables.
create table if not exists idempotency_key (
    key_value     varchar(512) not null primary key,
    processing    boolean      not null default true,
    result_ref    varchar(512),
    expires_at_ms bigint       not null
);

create index if not exists ix_idempotency_key_expiry on idempotency_key (expires_at_ms);
