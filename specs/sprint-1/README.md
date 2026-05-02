# Sprint 1

**Goal:** Establish the spec-first workflow and deliver the first end-to-end feature.

## Features

| Feature | Spec | Status |
|---------|------|--------|
| Add Income | [add-income.md](add-income.md) | ✅ Done |

## Workflow (for every feature going forward)

1. Write the spec in `specs/sprint-N/<feature>.md` — acceptance criteria first
2. Write the OpenAPI YAML spec → run `generate-sources`
3. Write failing domain unit tests (Given/When/Then)
4. Implement domain service to make them pass
5. Write failing integration test (the acceptance spec in code)
6. Implement controller + infrastructure to make that pass
7. Tick off the implementation checklist in the spec
