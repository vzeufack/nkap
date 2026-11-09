# Database ERD

Generated from the JPA entities in `src/main/java/com/kmercoders/nkap/`. Table and column names are the physical names produced by Spring Boot's default naming strategy (camelCase → snake_case).

```mermaid
erDiagram
    app_user {
        bigint id PK
        varchar email UK "not null"
        varchar password "not null, BCrypt"
    }

    authority {
        bigint id PK
        varchar authority_name "ROLE_USER / ROLE_ADMIN"
        bigint app_user_id FK
    }

    financial_institution {
        bigint id PK
        varchar institution_key UK "not null"
        varchar display_name "not null"
    }

    accounts {
        bigint id PK
        varchar account_type "CHECKING | SAVINGS | CASH | CREDIT"
        varchar name "not null"
        numeric balance "not null"
        bigint app_user_id FK "not null"
        bigint financial_institution_id FK "nullable"
    }

    budget {
        bigint id PK
        varchar budget_month "JANUARY..DECEMBER"
        int budget_year "not null"
        bigint app_user_id FK "not null"
    }

    budget_group {
        bigint id PK
        varchar name "not null"
        boolean is_default "not null"
    }

    budget_group_mapping {
        bigint budget_id PK, FK
        bigint group_id PK, FK
    }

    category {
        bigint id PK
        varchar name "not null, len 100"
        numeric balance "19,2 not null"
        bigint group_id FK "not null"
    }

    budget_category {
        bigint id PK
        bigint budget_id FK "not null"
        bigint category_id FK "not null"
        numeric allocation "19,2 not null"
    }

    transactions {
        bigint id PK
        numeric amount "19,2 not null, >= 0"
        date transaction_date "must fall in budget month/year"
        varchar direction "DEBIT | CREDIT"
        varchar transaction_type "STANDARD | ADJUSTMENT | TRANSFER"
        varchar description "len 100"
        varchar note "len 500"
        uuid transfer_id "links the two legs of a transfer"
        bigint budget_id FK "not null"
        bigint account_id FK "nullable"
        bigint budget_category_id FK "nullable"
    }

    app_user ||--o{ authority : "has roles"
    app_user ||--o{ accounts : owns
    app_user ||--o{ budget : owns
    financial_institution |o--o{ accounts : "held at"

    budget ||--o{ budget_group_mapping : ""
    budget_group ||--o{ budget_group_mapping : ""
    budget_group ||--o{ category : contains

    budget ||--o{ budget_category : allocates
    category ||--o{ budget_category : "allocated in"

    budget ||--o{ transactions : contains
    accounts |o--o{ transactions : "posted to"
    budget_category |o--o{ transactions : "categorized as"
```

## Constraints and notes

- **Unique keys**: `app_user.email`; `budget (app_user_id, budget_month, budget_year)` — one budget per user per month; `budget_category (budget_id, category_id)`; `financial_institution.institution_key`.
- **Budget ↔ Group** is many-to-many through `budget_group_mapping` (owned by `Budget.groups`).
- **Transactions attach to `budget_category`, not `category`**, so the same category can carry a different allocation and transaction set in each monthly budget.
- **`budget_group` and `category` have no `app_user_id`**: ownership is only reachable through `budget_group_mapping → budget → app_user`. A group not linked to any budget has no owner.
- **Cascades (JPA, not DB-level)**: deleting an `app_user` removes their budgets and accounts; deleting a `budget` removes its `budget_category` rows and transactions; deleting a `budget_group` removes its categories; deleting a `category` removes its `budget_category` rows, which in turn remove their transactions; deleting an `account` removes its transactions.
- `id` on `app_user`, `accounts`, and `authority` uses `GenerationType.AUTO` (sequence-backed); all other tables use `IDENTITY`.
