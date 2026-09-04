# Contract Delta: Exposed Instance Hardening

**Feature**: 024-exposed-instance-hardening | **Date**: 2026-09-04

No new endpoint. Two edits to the existing auth contract, in
`Application/src/main/resources/swagger/auth/`.

---

## 1. `auth-api-controller.yaml` — a new response on `/auth/login`

**Additive. Non-breaking.**

```yaml
  /auth/login:
    post:
      responses:
        "200":
          description: "Session issued"
          # unchanged
        "401":
          description: "Incorrect username or password (never says which)"
        "429":
          description: >
            Too many failed attempts. Returned before the submitted credentials are examined at
            all, and returned identically whether or not the username exists — it says that
            attempts are being refused, never anything about the account. Clears on its own after
            the refusal window; there is no endpoint that lifts it and no operator action that is
            needed.
        default:
          description: "Unexpected error"
```

---

## 2. `auth-model.yaml` — a real password minimum

**A restriction, not an addition.** See the plan's Constitution Check for why this is acceptable
without a `/v2` path.

```yaml
    setupRequest:
      properties:
        password:
          type: "string"
          minLength: 12          # was 1

    changePasswordRequest:
      properties:
        newPassword:
          type: "string"
          minLength: 12          # was 1

    loginRequest:
      properties:
        password:
          type: "string"
          # UNCHANGED — deliberately carries no minimum.
```

### Why `loginRequest` stays as it is

Constraining it would reject a short password before checking it, which tells the caller the stored
password is short — and would lock out any account created before this rule. The length of an
existing password is not the API's business (FR-010).

---

## Endpoints that change behaviour without changing shape

| Path | What changes | Contract change |
|---|---|---|
| `POST /auth/login` | May now answer 429 | Additive response |
| `POST /auth/setup` | Rejects a password under 12 | Restriction |
| `POST /auth/change-password` | Rejects a new password under 12 | Restriction |
| `/swagger-ui/**`, `/v3/api-docs/**` | Not served on a deployed instance | **None** — configuration, not contract. `SecurityConfig` is untouched (research R7) |

## Client impact

One client exists, in this repository. The frontend must:

- surface a 429 from login as "too many attempts, try again shortly" rather than as
  "incorrect password", which is what it would say today;
- state the 12-character minimum on the setup and change-password forms *before* submission, so the
  operator does not meet the rule as a rejection.

Both ship in the same change as the contract edit.
