-- Shared platform migration (ADR-0021). Applied into every service database that uses the outbox.
-- Reserved version range V900+ so service-owned migrations (V1..) never collide with platform tables.
create table if not exists outbox_record (
    id                varchar(64)  not null primary key,
    dedupe_key        varchar(512) not null,
    aggregate_type    varchar(128) not null,
    aggregate_id      varchar(256) not null,
    aggregate_version bigint       not null,
    topic             varchar(256) not null,
    partition_key     varchar(256),
    headers_json      text,
    payload           bytea        not null,
    created_at_ms     bigint       not null,
    state             varchar(32)  not null,
    attempt_count     int          not null default 0
);

create unique index if not exists ux_outbox_record_dedupe on outbox_record (dedupe_key);
create index if not exists ix_outbox_record_pending on outbox_record (state, created_at_ms);
