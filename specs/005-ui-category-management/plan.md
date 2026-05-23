# Implementation Plan: UI Category Management

**Branch**: `005-ui-category-management` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-ui-category-management/spec.md`

## Summary

Add a Categories view to the React frontend that lets users browse all categories (with type filtering) and create new ones via a modal form. The backend `GET /api/v1/categories` and `POST /api/v1/categories` endpoints already exist; only the frontend layer is new. Implementation follows the existing `Modal` + `useState` + `client.ts` patterns already established by `AddBillForm` and `AddIncomeForm`.

## Technical Context

**Language/Version**: TypeScript 5.x / React 18

**Primary Dependencies**: React 18, Vite 5, plain `fetch` API — no new dependencies required

**Storage**: N/A (frontend only; data persisted by Spring Boot backend + PostgreSQL)

**Testing**: Vitest + React Testing Library (Principle VI — test-first for form validation and hook logic)

**Target Platform**: Web browser, desktop-first; tested on latest Chrome/Firefox

**Project Type**: Single-page web application (frontend module of a full-stack personal finance app)

**Performance Goals**: Category list visible within 2 s; form submit feedback within 1 s

**Constraints**: No new npm dependencies; reuse existing `Modal`, CSS variables, and `client.ts` patterns

**Scale/Scope**: Personal finance app (single user); expected < 50 categories

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I — Transaction Immutability | ✅ N/A | Categories are reference data, not financial transactions |
| II — Double-Entry Accounting | ✅ N/A | No financial events involved |
| III — Account Integrity | ✅ N/A | No balance calculations |
| IV — Currency Precision | ✅ N/A | No monetary amounts |
| V — Audit Trail | ✅ Satisfied | Standard HTTP error logging already in `client.ts`; category creation is not a financial event |
| VI — Test-First | ⚠️ Required | Component tests for `AddCategoryForm` validation and `useCategories` hook must be written alongside implementation |
| VII — API Contract Stability | ✅ Satisfied | No new endpoints; consuming existing stable contracts. `Category` type update is additive (non-breaking) |
| VIII — Hexagonal Architecture | ✅ N/A | Frontend-only change; backend domain layer untouched |

**Post-design re-check**: All gates pass. No violations.

## Project Structure

### Documentation (this feature)

```text
specs/005-ui-category-management/
├── plan.md                        # This file
├── research.md                    # Phase 0 output
├── data-model.md                  # Phase 1 output
├── contracts/
│   └── component-contracts.md    # Phase 1 output
└── tasks.md                       # Phase 2 output (/speckit-tasks)
```

### Source Code Changes

```text
frontend/src/
├── api/
│   └── client.ts                  # add postAndReturn<T>(), createCategory(), fix fetchCategories()
├── types/
│   └── index.ts                   # add CategoryType, CreateCategoryRequest; update Category
├── hooks/
│   └── useCategories.ts           # new: fetch + refresh hook
├── components/
│   ├── AddCategoryForm.tsx        # new: modal form (name, type, color, parent)
│   ├── CategoryList.tsx           # new: list with type filter, empty state, color swatch
│   └── CategoriesPage.tsx         # new: page view wrapping list + form
└── App.tsx                        # add view toggle; render CategoriesPage
```

**Structure Decision**: Frontend-only, single project. Option 2 (web application) from template applies — only the `frontend/` subtree changes.

## Complexity Tracking

> No constitution violations — section not applicable.
