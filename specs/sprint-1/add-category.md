# Spec: Category

**Sprint:** 1  
**Status:** Draft — awaiting review

---

## Goal

Allow a user to create, retrieve, update, and delete categories so that bills and incomes can be organised and later used for spending analysis.

---

## User Story

> As a user, I want to manage categories with a name and type, so that I can organise my transactions and understand my spending patterns.

---

## Acceptance Criteria

### Create — happy path
- **Given** a valid request with a non-blank `name` and a `type`  
  **When** `POST /api/v1/categories` is called  
  **Then** the response is `201 Created` with the saved category including a generated `id`

### Create — validation
- **Given** a request with no `name`  
  **When** `POST /api/v1/categories` is called  
  **Then** the response is `400 Bad Request`

- **Given** a request with a blank `name` (empty string or whitespace only)  
  **When** `POST /api/v1/categories` is called  
  **Then** the response is `400 Bad Request`

- **Given** a request with no `type`  
  **When** `POST /api/v1/categories` is called  
  **Then** the response is `400 Bad Request`

- **Given** a category with the same `name` already exists  
  **When** `POST /api/v1/categories` is called  
  **Then** the response is `409 Conflict`

### Get
- **Given** a category exists with that id  
  **When** `GET /api/v1/categories/{id}` is called  
  **Then** the response is `200 OK` with the category

- **Given** no category exists with that id  
  **When** `GET /api/v1/categories/{id}` is called  
  **Then** the response is `404 Not Found`

### List
- **When** `GET /api/v1/categories` is called  
  **Then** the response is `200 OK` with all categories

- **When** `GET /api/v1/categories?type=EXPENSE` is called  
  **Then** the response is `200 OK` with only categories of type `EXPENSE`

### Update
- **Given** a category exists with that id and the new `name` is non-blank and not taken by another category  
  **When** `PUT /api/v1/categories/{id}` is called with a valid request body  
  **Then** the response is `200 OK` with the updated category

- **Given** no category exists with that id  
  **When** `PUT /api/v1/categories/{id}` is called  
  **Then** the response is `404 Not Found`

- **Given** a request with a blank `name` or no `type`  
  **When** `PUT /api/v1/categories/{id}` is called  
  **Then** the response is `400 Bad Request`

- **Given** the new `name` is already used by a different category  
  **When** `PUT /api/v1/categories/{id}` is called  
  **Then** the response is `409 Conflict`

### Delete
- **Given** a category exists and is not referenced by any bill or income  
  **When** `DELETE /api/v1/categories/{id}` is called  
  **Then** the response is `204 No Content` and the category is removed

- **Given** no category exists with that id  
  **When** `DELETE /api/v1/categories/{id}` is called  
  **Then** the response is `404 Not Found`

- **Given** a category is referenced by at least one bill or income  
  **When** `DELETE /api/v1/categories/{id}` is called  
  **Then** the response is `409 Conflict`

---

## API Contract

### POST /api/v1/categories

**Request body** (`createCategoryRequest`):

| Field              | Type   | Required | Notes                        |
|--------------------|--------|----------|------------------------------|
| `name`             | string | ✅       | Must be non-blank, unique    |
| `type`             | enum   | ✅       | EXPENSE, INCOME, BOTH        |
| `color`            | string | —        | e.g. hex code `#FF5733`      |
| `parentCategoryId` | string | —        | id of a parent category      |

**Responses:**
- `201 Created` — returns `categoryResponse`
- `400 Bad Request` — validation failed
- `409 Conflict` — name already exists

### GET /api/v1/categories

**Query parameters:**

| Param  | Type | Required | Notes                           |
|--------|------|----------|---------------------------------|
| `type` | enum | —        | Filter by EXPENSE, INCOME, BOTH |

**Responses:**
- `200 OK` — returns `categoryListResponse`

### GET /api/v1/categories/{id}

**Responses:**
- `200 OK` — returns `categoryResponse`
- `404 Not Found`

### PUT /api/v1/categories/{id}

**Request body** (`updateCategoryRequest`):

| Field              | Type   | Required | Notes                        |
|--------------------|--------|----------|------------------------------|
| `name`             | string | ✅       | Must be non-blank, unique    |
| `type`             | enum   | ✅       | EXPENSE, INCOME, BOTH        |
| `color`            | string | —        |                              |
| `parentCategoryId` | string | —        |                              |

**Responses:**
- `200 OK` — returns `categoryResponse`
- `400 Bad Request` — validation failed
- `404 Not Found`
- `409 Conflict` — name taken by another category

### DELETE /api/v1/categories/{id}

**Responses:**
- `204 No Content` — deleted successfully
- `404 Not Found`
- `409 Conflict` — category is referenced by a bill or income

---

## Domain Rules

1. `name` is required, must be non-blank, and must be unique across all categories
2. `type` is required — valid values: `EXPENSE`, `INCOME`, `BOTH`
3. A category cannot be deleted if it is referenced by any bill or income
4. `parentCategoryId` is optional — allows subcategories (e.g. "Restaurants" under "Food")
5. `color` is optional — used for UI display only, no format enforced at domain level
6. The `id` is generated by the database (UUID)

---

## Implementation Checklist

- [ ] OpenAPI spec (`category-model.yaml`, `category-post-controller.yaml`, `category-get-controller.yaml`, `category-put-controller.yaml`, `category-delete-controller.yaml`)
- [ ] Domain unit tests (`AddCategoryServiceImplTest`, `UpdateCategoryServiceImplTest`, `DeleteCategoryServiceImplTest`)
- [ ] `AddCategoryService`, `GetCategoryService`, `UpdateCategoryService`, `DeleteCategoryService` API ports
- [ ] `AddCategoryPersistencePort`, `GetCategoryPersistencePort`, `UpdateCategoryPersistencePort`, `DeleteCategoryPersistencePort` SPI ports
- [ ] Service implementations with validation
- [ ] `CategoryEntity`, `CategoryRepository`, `CategoryMapper` (Infrastructure)
- [ ] Persistence adapters (add, get, update, delete)
- [ ] `CategoryMapper` (Application)
- [ ] `CategoryCreateController`, `CategoryGetController`, `CategoryUpdateController`, `CategoryDeleteController`
- [ ] Integration test (`CategoryControllerIntegrationTest`)
