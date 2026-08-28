# Phase 1 Data Model: Setup Templates for Categories and Accounts

No new persisted entity, table, or Domain DTO — per `research.md`, template definitions are a
static frontend constant, and applying one only calls the existing `POST /categories`/
`POST /accounts` endpoints. What follows is the new client-side-only shape.

## Client-side types (new)

### `TemplateCategoryItem` (new interface)

| Field | Type | Notes |
|---|---|---|
| `name` | `string` | The category name to create, e.g. `"Groceries"`. Unique within a template by construction (the static data never lists the same name twice within one template). |
| `type` | `CategoryType` (existing enum: `EXPENSE` \| `INCOME` \| `BOTH`) | The category type to create it with. |

### `TemplateAccountItem` (new interface)

| Field | Type | Notes |
|---|---|---|
| `name` | `string` | The account name to create, e.g. `"Checking"`. |
| `type` | `AccountType` (existing enum: `CHECKING` \| `SAVINGS` \| `CREDIT_CARD` \| `CASH` \| `INVESTMENT`) | The account type to create it with. |

Per the spec's Assumptions, account items always use a single default currency (`EUR`) as both
the account's currency and its default currency, no starting balance, and no institution — these
aren't per-item fields, they're a fixed constant `applySetupTemplate()` applies uniformly (see
below), so there's nothing to vary per item beyond name and type.

### `SetupTemplate` (new interface)

| Field | Type | Notes |
|---|---|---|
| `id` | `string` | Stable identifier for the template, e.g. `"personal-finance-starter"`. |
| `name` | `string` | Display name, e.g. `"Personal Finance Starter"`. |
| `description` | `string` | One-line description shown when browsing templates. |
| `categoryItems` | `TemplateCategoryItem[]` | Every category this template offers. |
| `accountItems` | `TemplateAccountItem[]` | Every account this template offers. |

`SETUP_TEMPLATES: SetupTemplate[]` (in `frontend/src/data/setupTemplates.ts`) is the fixed,
hardcoded list — one entry, `"personal-finance-starter"`, for this feature's initial ship, per the
spec's Assumptions section.

### `ApplyTemplateResult` (new interface)

| Field | Type | Notes |
|---|---|---|
| `created` | `string[]` | Names of items actually created (category and account names mixed, in the template's own item order). |
| `skipped` | `string[]` | Names of items skipped because a category/account with that exact name already existed. |

## Derived behavior (not stored): applying a template

```
applySetupTemplate(template: SetupTemplate, selectedKeys: Set<string>): Promise<ApplyTemplateResult> =
    created = []; skipped = []
    for item in template.categoryItems:
        key = "category:" + item.name
        if key not in selectedKeys: continue
        outcome = POST /categories { name: item.name, type: item.type }
                  -> 409 Conflict => 'skipped'
                  -> 2xx          => 'created'
        (created if outcome == 'created' else skipped).push(item.name)
    for item in template.accountItems:
        key = "account:" + item.name
        if key not in selectedKeys: continue
        outcome = POST /accounts { name: item.name, type: item.type,
                                    currencies: ["EUR"], defaultCurrency: "EUR" }
                  -> 409 Conflict => 'skipped'
                  -> 2xx          => 'created'
        (created if outcome == 'created' else skipped).push(item.name)
    return { created, skipped }
```

`selectedKeys` is the UI's per-item checkbox state (FR-006), defaulting to *every* item's key
selected when a template's preview first opens (so the common case — apply everything — needs no
extra clicks). A `"category:"`/`"account:"` prefix on each key avoids an accidental collision
between a category and an account that happen to share a name within the same template (none do
in the initial template, but the key scheme holds regardless).

## Relationships

```
SetupTemplate ──contains──> TemplateCategoryItem[] / TemplateAccountItem[]   (static, in-memory only)
TemplateCategoryItem ··applied via·· POST /categories  (existing endpoint, unchanged)
TemplateAccountItem  ··applied via·· POST /accounts    (existing endpoint, unchanged)
```

No relationship to any new persisted entity exists, because none is introduced. The `Category` and
`Account` rows a template application creates are ordinary rows — nothing marks them as
template-derived (FR-009).

## Validation Rules

- Selecting zero items and attempting to apply is prevented client-side (the Apply action is
  disabled when `selectedKeys` is empty) rather than allowed to silently no-op (FR-007).
- No other client-side validation is introduced — every item's `name`/`type` (and, for accounts,
  the fixed `currencies`/`defaultCurrency`) already satisfies `AddCategoryServiceImpl`/
  `AddAccountServiceImpl`'s existing validation by construction (the static template data is
  well-formed), so a validation failure from the create endpoints is not an expected code path in
  normal operation.

## State Transitions

None — `SetupTemplate`/`TemplateCategoryItem`/`TemplateAccountItem` are static constants with no
lifecycle. `ApplyTemplateResult` is a one-shot value returned from a single `applySetupTemplate()`
call, not a persisted or evolving state.
