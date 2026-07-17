-- V1 — Baseline schema.
--
-- Captures the schema Hibernate used to own under ddl-auto=update, so the DB becomes
-- migration-driven while Hibernate is demoted to ddl-auto=validate. The DDL below was
-- generated from the JPA entities against pgvector:pg16 and must stay byte-compatible with
-- what `validate` expects — column types are exact (Instant -> timestamp(6) with time zone).
--
-- Idempotent (CREATE ... IF NOT EXISTS) on purpose: an existing dev database already carrying
-- these tables from ddl-auto=update is baselined at version 0 (see spring.flyway.baseline-version),
-- then V1 runs as a no-op; a fresh database gets the tables created here.
--
-- NOT owned by this migration (kept out by design):
--   * the `vector_store` table and its IVFFlat index — owned by LangChain4j's
--     PgVectorEmbeddingStore + JpaKnowledgeRepository.rebuildIndex() (ARCHITECTURE Design Decision #3).

-- Extensions — required by the knowledge base. Idempotent; normally pre-provisioned by
-- docker/postgres/init.sql and the knowledge repository at runtime, declared here so a bare
-- Postgres can be brought up from migrations alone.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ── sessions ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sessions (
    id            uuid                        NOT NULL,
    first_seen_at timestamp(6) with time zone NOT NULL,
    last_seen_at  timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id)
);

-- ── invoices ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS invoices (
    id                     uuid        NOT NULL,
    session_id             uuid,
    file_name              varchar(255) NOT NULL,
    supply_type            varchar(15)  NOT NULL
        CHECK (supply_type IN ('ELECTRICITY','GAS','WATER','TELECOM','OTHER')),
    provider               varchar(255) NOT NULL,
    uploaded_at            timestamp(6) with time zone NOT NULL,
    -- Common extracted fields
    billing_period_start   date,
    billing_period_end     date,
    total_amount           numeric(10,2),
    raw_text_redacted      TEXT,
    -- Electricity
    consumption_kwh        numeric(10,3),
    consumption_kwh_p1     numeric(10,3),
    consumption_kwh_p2     numeric(10,3),
    consumption_kwh_p3     numeric(10,3),
    price_per_kwh          numeric(10,6),
    price_per_kwh_p1       numeric(10,6),
    price_per_kwh_p2       numeric(10,6),
    price_per_kwh_p3       numeric(10,6),
    contracted_power_kw    numeric(6,3),
    -- Gas + water
    consumption_m3         numeric(10,3),
    price_per_m3           numeric(10,6),
    -- Water
    sewage_charge          numeric(10,2),
    -- Telecom
    contracted_speed_mbps  integer,
    mobile_data_gb         integer,
    phone_lines            integer,
    mobile_lines_json      TEXT,
    included_mobile_lines  integer,
    mobile_line_count      integer,
    streaming_services_json TEXT,
    monthly_fee            numeric(10,2),
    PRIMARY KEY (id)
);

-- ── knowledge_documents ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id            uuid        NOT NULL,
    doc_type      varchar(20)  NOT NULL
        CHECK (doc_type IN ('CNMC_CIRCULAR','REE_GUIDE','BOE_REGULATION','GLOSSARY','GENERAL')),
    supply_domain varchar(15)  NOT NULL
        CHECK (supply_domain IN ('ELECTRICITY','GAS','WATER','TELECOM','OTHER')),
    title         varchar(255) NOT NULL,
    source        varchar(255) NOT NULL,
    valid_from    date,
    valid_to      date,
    created_at    timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id)
);

-- ── knowledge_chunks ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id           uuid        NOT NULL,
    document_id  uuid        NOT NULL,
    embedding_id varchar(255) NOT NULL,
    content      TEXT        NOT NULL,
    section      varchar(255),
    chunk_index  integer     NOT NULL,
    PRIMARY KEY (id)
);

-- ── electricity_rates ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS electricity_rates (
    id                        uuid        NOT NULL,
    supply_type               varchar(15)  NOT NULL
        CHECK (supply_type IN ('ELECTRICITY','GAS','WATER','TELECOM','OTHER')),
    company                   varchar(255) NOT NULL,
    tariff_name               varchar(255) NOT NULL,
    price_per_kwh             numeric(10,6),
    price_per_kwh_valle       numeric(10,6),
    price_per_kwh_llano       numeric(10,6),
    price_per_kwh_punta       numeric(10,6),
    contracted_power_price    numeric(10,6),
    contracted_power_price_p2 numeric(10,6),
    valid_from                date        NOT NULL,
    valid_to                  date,
    region                    varchar(255),
    source                    varchar(255) NOT NULL,
    received_at               timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id)
);