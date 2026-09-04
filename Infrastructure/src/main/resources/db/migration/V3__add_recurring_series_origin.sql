-- =============================================================================
-- V3 - Provenance for transactions the app posted itself (feature 023)
-- =============================================================================
--
-- A confirmed recurring series now writes its own transactions. Constitution Principle V says that
-- when the pipeline writes on the operator's behalf, "why is this row here?" must be answerable -
-- and that this matters MORE once rows arrive automatically, not less. This column is that answer.
--
-- THREE ORIGINS, TWO NULLABLE COLUMNS
--
--   hand-entered  external_id IS NULL      recurring_series_id IS NULL
--   imported      external_id IS NOT NULL  recurring_series_id IS NULL
--   auto-posted   external_id IS NOT NULL  recurring_series_id IS NOT NULL
--
-- No `origin` enum column was added. It would be derivable from the two facts above, and a second
-- encoding of the same truth is a second thing that can disagree with the first. Feature 022
-- rejected an origin column on the same grounds; the reasoning holds with three origins.
--
-- The occurrence's period is not stored either: an auto-posted transaction is dated at its own
-- occurrence, so the period is recoverable from the row.
--
-- WHY IT IS INDEXED
--   Reconciliation runs on every statement import: for each incoming transaction it looks for an
--   auto-posted prediction of the same series to supersede. That is a lookup per imported row, so
--   it must not be a sequential scan of the whole ledger.
--
-- Uniqueness of an auto-posted occurrence is NOT enforced here - it is already enforced by V2's
-- partial unique index on (account_id, external_id), with the identity `recurring:<seriesId>:<date>`.
-- That is what makes posting the same period twice impossible however many times the job runs, and
-- what makes catching up after downtime safe rather than merely careful.
--
-- Do not edit an applied migration - Flyway checksums them. The next schema change is V4.
-- =============================================================================

alter table bill add column recurring_series_id varchar(255);

create index idx_bill_recurring_series_id
    on bill (recurring_series_id)
    where recurring_series_id is not null;

alter table income add column recurring_series_id varchar(255);

create index idx_income_recurring_series_id
    on income (recurring_series_id)
    where recurring_series_id is not null;
