# Quickstart: Spending Analysis API

**Feature**: 001-spending-analysis-api
**Date**: 2026-05-23

## Prerequisites

- Docker Compose stack running: `docker-compose up`
- At least one `bill` and one `income` entry in the database

## Endpoints

### Get Monthly Summary

```
GET /api/v1/analysis/monthly?year=2026&month=5
```

**Example response**:

```json
{
  "common": { "success": true, "message": "OK" },
  "summary": {
    "year": 2026,
    "month": 5,
    "totalIncome": 3500.00,
    "totalExpenses": 1200.50,
    "netBalance": 2299.50,
    "spendingByCategory": {
      "a1b2c3d4-...": 420.00,
      "e5f6a7b8-...": 150.00
    }
  }
}
```

**Error cases**:
- `month` outside 1–12 → `400 Bad Request`
- Missing `year` or `month` → `400 Bad Request`

---

### Get Period Summary

```
GET /api/v1/analysis/period?from=2026-01-01&to=2026-03-31
```

Returns one entry per calendar month in the range.

**Example response**:

```json
{
  "common": { "success": true, "message": "OK" },
  "summaries": [
    { "year": 2026, "month": 1, "totalIncome": 3500.00, "totalExpenses": 900.00, "netBalance": 2600.00, "spendingByCategory": {} },
    { "year": 2026, "month": 2, "totalIncome": 3500.00, "totalExpenses": 1100.00, "netBalance": 2400.00, "spendingByCategory": {} },
    { "year": 2026, "month": 3, "totalIncome": 3500.00, "totalExpenses": 1200.00, "netBalance": 2300.00, "spendingByCategory": {} }
  ]
}
```

**Error cases**:
- `from` after `to` → `400 Bad Request`
- Invalid date format → `400 Bad Request`

---

## Swagger UI

When the app is running, explore the endpoints at:

```
http://localhost:8080/swagger-ui.html
```

Look for the **AnalysisGetController** tag.
