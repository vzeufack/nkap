# Nkap

Personal budgeting web app.

## Overview

Nkap is a personal budgeting application built with Spring Boot 3.3.2 and Java 17. The primary budget UI is server-rendered with Thymeleaf and uses htmx-style partial page updates, while most CRUD operations (accounts, categories, groups, transactions, bulk upload) are exposed as JSON REST APIs consumed by page-level JavaScript.

## Tech stack

- Java 17
- Spring Boot 3.3.2 (Web, Data JPA, Security, Thymeleaf, Validation)
- PostgreSQL 18 (runtime), H2 (tests)
- Flyway (schema migrations, production only)
- Gradle wrapper
- Docker + nginx reverse proxy (deployment)

## Getting started (local dev)

**Prerequisites:** JDK 17 and a local PostgreSQL instance reachable at `jdbc:postgresql://localhost:5432/nkapdb` (default credentials `admin` / `password`, configurable in `application.properties`).

```bash
./gradlew bootRun    # run the app locally
```

The default profile uses `spring.jpa.hibernate.ddl-auto=update`, so the schema is created/updated automatically against your local Postgres database.

```bash
./gradlew test       # run the test suite (in-memory H2, no external DB needed)
```

## Running with Docker

`docker-compose.yml` defines three services:

- **db** — PostgreSQL 18
- **app** — built from the `Dockerfile`, runs with the `prod` profile, schema managed by Flyway (`ddl-auto=validate`)
- **nginx** — reverse proxy in front of `app`; the only service that publishes a host port (`8080:80`)

```bash
docker compose up --build
```

Then visit `http://localhost:8080`.

## Database migrations

Flyway migrations live in `src/main/resources/db/migration/` (currently `V1__init.sql`). Flyway only runs in the `prod` profile — the default/dev profile relies on `ddl-auto=update`, and the `test` profile uses `ddl-auto=create-drop`.

## Project structure

Code is organized by domain feature under `src/main/java/com/kmercoders/nkap/`: `account`, `appuser`, `budget`, `bulkupload`, `category`, `financialinstitution`, `group`, `transaction`, plus a shared `exception` package. See [CLAUDE.md](CLAUDE.md) for a deeper dive into the architecture, domain model, and conventions.

## Testing

Controller tests use `@SpringBootTest` with `MockMvc` against the `test` profile (in-memory H2). Run a single test class or method with:

```bash
./gradlew test --tests "com.kmercoders.nkap.transaction.TransactionControllerTest"
./gradlew test --tests "com.kmercoders.nkap.transaction.TransactionControllerTest.createTransaction_withAllFields_returns200AndCorrectPayload"
```
