# Quickstart: Frontend Dashboard

**Feature**: 003-frontend-dashboard
**Date**: 2026-05-23

## Prerequisites

- Backend running: `docker-compose up` (or `./mvnw -pl Launcher spring-boot:run`)
- Node.js installed

## Start the Dashboard

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` in a browser.

## What You See

The dashboard opens on the **current calendar month** and shows four sections:

| Section | Data source |
|---------|-------------|
| Monthly Summary | `GET /api/v1/analysis/monthly?year=&month=` |
| Category Spending | `spendingByCategory` from the summary response |
| Budget vs. Actual | `GET /api/v1/budgets/status?year=&month=` |
| Recent Transactions | `GET /api/v1/bills` + `GET /api/v1/incomes` (client-side filtered) |

## Month Navigation

Click **‹** to go to the previous month, **›** to go to the next month. All four sections
refresh automatically.

## Seeding Test Data

To see non-empty sections, create some data first via the backend APIs or Swagger UI
(`http://localhost:8080/swagger-ui.html`):

1. Create a category: `POST /api/v1/categories`
2. Add some bills: `POST /api/v1/bills`
3. Add some income: `POST /api/v1/incomes`
4. Set a budget: `POST /api/v1/budgets`

Then reload the dashboard for the same year/month.

## Currency Display

All amounts are formatted in Euro using the local number format (e.g. `€ 1.234,56`).
Net balance is shown in **green** when positive and **red** when negative.
