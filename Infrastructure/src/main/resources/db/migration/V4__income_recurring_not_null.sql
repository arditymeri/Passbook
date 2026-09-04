-- =============================================================================
-- V4 - income.recurring can no longer be NULL (bug fix)
-- =============================================================================
--
-- WHAT WENT WRONG
--
-- `IncomeEntity.recurring` is a primitive `boolean`, so Hibernate cannot read a row where the
-- column is NULL - it throws `IllegalArgumentException: Can not set boolean field ... to null
-- value`. Feature 022's statement ingestion writes income rows with hand-written SQL, and its
-- column list omitted `recurring`. Every imported income therefore landed with NULL.
--
-- The failure is nowhere near its cause. The import itself succeeds. What breaks is every LATER
-- read of the income table: account balances, budget status, savings goals, recurring detection,
-- and the next import. An operator would see their app stop working and have no reason to connect
-- it to a statement they imported days earlier.
--
-- The insert is fixed in `IngestTransactionsPostgresAdapter`. This migration does the other two
-- halves: it repairs rows already written, and it makes the column refuse NULL so no future
-- hand-written insert can reintroduce the same state silently. The column is now shaped exactly
-- like `reversal`, which had it right from the start.
--
-- `bill` needs no equivalent: that table has no `recurring` column at all.
--
-- Do not edit an applied migration - Flyway checksums them. The next schema change is V5.
-- =============================================================================

update income set recurring = false where recurring is null;

alter table income alter column recurring set default false;

alter table income alter column recurring set not null;
