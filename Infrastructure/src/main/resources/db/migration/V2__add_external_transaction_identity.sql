-- =============================================================================
-- V2 - External transaction identity (feature 022, idempotent statement ingestion)
-- =============================================================================
--
-- Implements Constitution Principle II ("Ingestion Is Idempotent", NON-NEGOTIABLE), which has
-- required a stable external identity on every transaction arriving from outside the UI since
-- ratification and has never had anywhere to store one.
--
-- WHAT external_id HOLDS
--   * the bank's own transaction id, when the statement supplies one; otherwise
--   * a deterministic hash of (account, calendar date, amount, description, direction) followed by
--     ':' and an occurrence index.
--
-- The occurrence index is what lets two genuinely identical rows - the same coffee bought twice on
-- the same day - both be recorded, while re-importing that same statement stays a no-op. Direction
-- is part of the hash because bill and income both store positive amounts: without it a refund
-- would collide with the charge it reverses.
--
-- WHY THE INDEX IS PARTIAL
--   Two populations of rows must coexist with no identity at all, and neither may be blocked:
--     * transactions typed by hand, which have no external identity by definition; and
--     * transactions recorded before this migration, which are deliberately NOT backfilled -
--       inventing identities for history that was never ingested would assert a provenance that
--       did not happen.
--   PostgreSQL already treats NULLs as distinct in a unique index, so the predicate is not what
--   permits those rows; it states the intent, and keeps the index to ingested rows rather than
--   indexing every manual entry ever made.
--
-- WHY UNIQUENESS LIVES HERE AND NOT IN APPLICATION CODE
--   The application must never decide "have I seen this?" by looking before writing. Two imports of
--   overlapping statements running at the same moment would both look, both see nothing, and both
--   write. This index is what arbitrates instead, so ingestion can use
--   INSERT ... ON CONFLICT DO NOTHING RETURNING and learn from the write itself which rows landed.
--
-- ADD COLUMN with no default is a catalogue-only operation in modern PostgreSQL, so this does not
-- rewrite an operator's table.
--
-- Do not edit an applied migration - Flyway checksums them. The next schema change is V3.
-- =============================================================================

alter table bill add column external_id varchar(255);

create unique index uq_bill_account_external_id
    on bill (account_id, external_id)
    where external_id is not null;

alter table income add column external_id varchar(255);

create unique index uq_income_account_external_id
    on income (account_id, external_id)
    where external_id is not null;
