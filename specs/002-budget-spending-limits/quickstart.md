# Quickstart: Budget / Spending Limits

**Feature**: 002-budget-spending-limits
**Date**: 2026-05-23

## Prerequisites

- Docker Compose stack running: `docker-compose up`
- At least one category exists (needed to create a budget)

## Workflow

### 1. Create a budget

```
POST /api/v1/budgets
Content-Type: application/json

{
  "categoryId": "your-category-uuid",
  "year": 2026,
  "month": 5,
  "limitAmount": 500.00
}
```

**Response (200 OK)**:
```json
{
  "id": "budget-uuid",
  "categoryId": "your-category-uuid",
  "year": 2026,
  "month": 5,
  "limitAmount": 500.00
}
```

Calling POST again with the same `categoryId + year + month` updates the limit (upsert).

**Error cases**:
- `limitAmount ≤ 0` → `400 Bad Request`
- `categoryId` not found → `404 Not Found`
- Invalid `month` (e.g., 13) → `400 Bad Request`

---

### 2. List budgets for a month

```
GET /api/v1/budgets?year=2026&month=5
```

**Response (200 OK)**:
```json
{
  "budgets": [
    { "id": "uuid-1", "categoryId": "cat-groceries", "year": 2026, "month": 5, "limitAmount": 500.00 },
    { "id": "uuid-2", "categoryId": "cat-transport", "year": 2026, "month": 5, "limitAmount": 150.00 }
  ]
}
```

---

### 3. View budget vs actual status

```
GET /api/v1/budgets/status?year=2026&month=5
```

**Response (200 OK)**:
```json
{
  "year": 2026,
  "month": 5,
  "entries": [
    {
      "categoryId": "cat-groceries",
      "budgeted": 500.00,
      "actual": 420.00,
      "remaining": 80.00,
      "status": "UNDER_BUDGET"
    },
    {
      "categoryId": "cat-entertainment",
      "budgeted": 100.00,
      "actual": 150.00,
      "remaining": -50.00,
      "status": "OVER_BUDGET"
    }
  ]
}
```

---

### 4. Delete a budget

```
DELETE /api/v1/budgets/{id}
```

**Response**: `204 No Content`

**Error cases**:
- Budget ID not found → `404 Not Found`

---

## Swagger UI

```
http://localhost:8080/swagger-ui.html
```

Look for **budgetCreate**, **budgetGet**, and **budgetDelete** tags.
