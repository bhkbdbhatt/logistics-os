-- MDM system-of-record (ADR-0021). Service-owned migration; the shared outbox/idempotency tables come
-- from platform:persistence (classpath:io/logisticsos/persistence/migration, reserved V900+).
create table if not exists mdm_golden_record (
    entity         varchar(64)  not null,
    id             varchar(256) not null,
    tenant_id      varchar(64)  not null,
    version        bigint       not null,
    active         boolean      not null,
    canonical_json text         not null,
    primary key (entity, id)
);

create index if not exists ix_mdm_golden_record_tenant on mdm_golden_record (tenant_id, entity);
