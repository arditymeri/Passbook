# Quickstart: Frontend Data Entry

**Feature**: 004-frontend-data-entry
**Date**: 2026-05-23

## Prerequisites

- Backend running at `http://localhost:8080`
- Frontend dev server running: `cd frontend && npm run dev`
- At least one category created (optional, but needed for category assignment)

## Recording an Expense

1. Open `http://localhost:5173`
2. Click **"+ Add Expense"** in the dashboard header
3. Enter an amount (e.g. `42.50`), select today's date, optionally add a description and category
4. Click **Save** — the form closes and the dashboard refreshes
5. The new expense appears in the monthly summary and (if categorised) in the spending breakdown

## Recording an Income

1. Click **"+ Add Income"** in the dashboard header
2. Enter an amount, date, optional description and source (e.g. SALARY)
3. Click **Save** — the form closes and the dashboard refreshes
4. The new income appears in the monthly summary total income figure

## Validation

- Submitting with amount = 0 or empty shows: *"Amount must be greater than zero"*
- The Save button is disabled while the request is in flight
- If the backend returns an error, the form stays open with: *"Could not save — please try again"*

## Cancelling

- Click **Cancel** or press **Escape** to close the form without saving
