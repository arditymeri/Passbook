# Spec: Account

**Sprint:** 1  
**Status:** Draft — awaiting review

---

## Goal

Allow a user to create and manage accounts (e.g. bank account, cash wallet, credit card) so that bills and incomes can be associated with the account they belong to.

---

## User Story

> As a user, I want to manage my financial accounts with a name, type, and one or more currencies, so that I can track which account each bill or income belongs to.

---

## Acceptance Criteria

### Create — happy path
- **Given** a valid request with a non-blank `name`, a `type`, at least one valid ISO 4217 currency in `currencies`, and a `defaultCurrency` that is present in `currencies`  
  **When** `POST /api/v1/accounts` is called  
  **Then** the response is `201 Created` with the saved account including a generated `id`

### Create — validation
- **Given** a request with no `name`  
  **When** `POST /api/v1/accounts` is called  
  **Then** the response is `400 Bad Request`

- **Given** a request with a blank `name`  
  **When** `POST /api/v1/accounts` is called  
  **Then** the response is `400 Bad Request`

- **Given** a request with no `type`  
  **When** `POST /api/v1/accounts` is called  
  **Then** the response is `400 Bad Request`

- **Given** a request with an empty `currencies` list  
  **When** `POST /api/v1/accounts` is called  
  **Then** the response is `400 Bad Request`

- **Given** a request with a currency code that is not valid ISO 4217 (e.g. `"XYZ"`)  
  **When** `POST /api/v1/accounts` is called  
  **Then** the response is `400 Bad Request`

- **Given** a `defaultCurrency` that is not present in the `currencies` list  
  **When** `POST /api/v1/accounts` is called  
  **Then** the response is `400 Bad Request`

- **Given** an account with the same `name` already exists  
  **When** `POST /api/v1/accounts` is called  
  **Then** the response is `409 Conflict`

### Get
- **Given** an account exists with that id  
  **When** `GET /api/v1/accounts/{id}` is called  
  **Then** the response is `200 OK` with the account

- **Given** no account exists with that id  
  **When** `GET /api/v1/accounts/{id}` is called  
  **Then** the response is `404 Not Found`

### List
- **When** `GET /api/v1/accounts` is called  
  **Then** the response is `200 OK` with all accounts

- **When** `GET /api/v1/accounts?type=SAVINGS` is called  
  **Then** the response is `200 OK` with only accounts of that type

### Update
- **Given** an account exists and the request is valid  
  **When** `PUT /api/v1/accounts/{id}` is called  
  **Then** the response is `200 OK` with the updated account

- **Given** no account exists with that id  
  **When** `PUT /api/v1/accounts/{id}` is called  
  **Then** the response is `404 Not Found`

- **Given** a blank `name`, missing `type`, empty `currencies`, invalid currency code, or `defaultCurrency` not in `currencies`  
  **When** `PUT /api/v1/accounts/{id}` is called  
  **Then** the response is `400 Bad Request`

- **Given** the new `name` is already used by a different account  
  **When** `PUT /api/v1/accounts/{id}` is called  
  **Then** the response is `409 Conflict`

### Delete
- **Given** an account exists and is not referenced by any bill or income  
  **When** `DELETE /api/v1/accounts/{id}` is called  
  **Then** the response is `204 No Content`

- **Given** no account exists with that id  
  **When** `DELETE /api/v1/accounts/{id}` is called  
  **Then** the response is `404 Not Found`

- **Given** an account is referenced by at least one bill or income  
  **When** `DELETE /api/v1/accounts/{id}` is called  
  **Then** the response is `409 Conflict`

---

## API Contract

### POST /api/v1/accounts

**Request body** (`createAccountRequest`):

| Field             | Type            | Required | Notes                                              |
|-------------------|-----------------|----------|----------------------------------------------------|
| `name`            | string          | ✅       | Must be non-blank, unique                          |
| `type`            | enum            | ✅       | CHECKING, SAVINGS, CREDIT_CARD, CASH, INVESTMENT   |
| `currencies`      | array of string | ✅       | At least one entry; each must be a valid ISO 4217 code (e.g. `EUR`, `USD`) |
| `defaultCurrency` | string          | ✅       | Must be present in `currencies`                    |
| `balance`         | number          | —        | Initial balance; defaults to `0` if not provided   |
| `institution`     | string          | —        | e.g. bank name                                     |

**Responses:**
- `201 Created` — returns `accountResponse`
- `400 Bad Request` — validation failed
- `409 Conflict` — name already exists

### GET /api/v1/accounts

**Query parameters:**

| Param  | Type | Required | Notes                                                      |
|--------|------|----------|------------------------------------------------------------|
| `type` | enum | —        | Filter by CHECKING, SAVINGS, CREDIT_CARD, CASH, INVESTMENT |

**Responses:**
- `200 OK` — returns `accountListResponse`

### GET /api/v1/accounts/{id}

**Responses:**
- `200 OK` — returns `accountResponse`
- `404 Not Found`

### PUT /api/v1/accounts/{id}

**Request body** (`updateAccountRequest`):

| Field             | Type            | Required | Notes                              |
|-------------------|-----------------|----------|------------------------------------|
| `name`            | string          | ✅       | Must be non-blank, unique          |
| `type`            | enum            | ✅       |                                    |
| `currencies`      | array of string | ✅       | At least one valid ISO 4217 code   |
| `defaultCurrency` | string          | ✅       | Must be present in `currencies`    |
| `balance`         | number          | —        |                                    |
| `institution`     | string          | —        |                                    |

**Responses:**
- `200 OK` — returns `accountResponse`
- `400 Bad Request`
- `404 Not Found`
- `409 Conflict`

### DELETE /api/v1/accounts/{id}

**Responses:**
- `204 No Content`
- `404 Not Found`
- `409 Conflict` — account is referenced by a bill or income

---

## Domain Rules

1. `name` is required, must be non-blank, and must be unique across all accounts
2. `type` is required — valid values: `CHECKING`, `SAVINGS`, `CREDIT_CARD`, `CASH`, `INVESTMENT`
3. `currencies` must contain at least one entry; each code must be a valid ISO 4217 currency code
4. `defaultCurrency` is required and must be one of the values in `currencies`
5. `balance` defaults to `0` if not provided
6. An account cannot be deleted if it is referenced by any bill or income
7. The `id` is generated by the database (UUID)

---

## Open Questions

- Should updating `currencies` be allowed if the account already has transactions in a currency being removed?

---

## Implementation Checklist

- [ ] OpenAPI spec (`account-model.yaml`, `account-post-controller.yaml`, `account-get-controller.yaml`, `account-put-controller.yaml`, `account-delete-controller.yaml`)
- [ ] Domain unit tests (`AddAccountServiceImplTest`, `UpdateAccountServiceImplTest`, `DeleteAccountServiceImplTest`)
- [ ] `AddAccountService`, `GetAccountService`, `UpdateAccountService`, `DeleteAccountService` API ports
- [ ] `AddAccountPersistencePort`, `GetAccountPersistencePort`, `UpdateAccountPersistencePort`, `DeleteAccountPersistencePort` SPI ports
- [ ] Service implementations with validation
- [ ] `AccountEntity`, `AccountRepository`, `AccountMapper` (Infrastructure)
- [ ] Persistence adapters (add, get, update, delete)
- [ ] `AccountMapper` (Application)
- [ ] `AccountCreateController`, `AccountGetController`, `AccountUpdateController`, `AccountDeleteController`
- [ ] Integration test (`AccountControllerIntegrationTest`)
