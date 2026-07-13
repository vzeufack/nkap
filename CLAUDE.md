# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Nkap is a personal budgeting web app built with Spring Boot 3.3.2 / Java 17. Server-rendered pages (Thymeleaf) handle the primary budget UI with htmx-style partial updates, while most CRUD operations (accounts, categories, groups, transactions) are exposed as JSON REST APIs consumed by page-level JS (`src/main/resources/static/js/`).

## Common commands

```bash
./gradlew build              # compile + run tests
./gradlew test               # run all tests
./gradlew test --tests "com.kmercoders.nkap.transaction.TransactionControllerTest"          # single test class
./gradlew test --tests "com.kmercoders.nkap.transaction.TransactionControllerTest.createTransaction_withAllFields_returns200AndCorrectPayload"  # single test method
./gradlew bootRun            # run the app locally
```

Tests run against an in-memory H2 database via the `test` Spring profile (`application-test.properties`); no external DB is needed to run the test suite. Running the app itself (`bootRun`) requires a local Postgres at `jdbc:postgresql://localhost:5432/nkap` (see `application.properties`). Note `spring.jpa.hibernate.ddl-auto=create` — the schema is dropped and recreated on every app startup, so local Postgres data does not persist across restarts.

## Architecture

### Package-by-feature

Code under `src/main/java/com/kmercoders/nkap/` is organized by domain feature, not by layer: `account/`, `appuser/`, `budget/`, `category/`, `group/`, `transaction/`, plus a shared `exception/` package. Each feature package typically contains its own `Entity`, `Repository`, `Service`, `Controller`, and (for REST endpoints) `DTO`/`Request` classes.

### Domain model

- `AppUser` owns a list of `Budget`s and `Account`s (implements Spring Security's `UserDetails`; email is the username).
- `Budget` is unique per `(appUser, month, year)`. It has a many-to-many relationship with `Group` (join table `budget_group_mapping`) and owns `BudgetCategory` and `Transaction` collections.
- `Group` (table `budget_group`) owns a list of `Category`. One group can be flagged `isDefault`.
- `BudgetCategory` (table `budget_category`) links a `Budget` to a `Category` with an `allocation` amount — this is the entity transactions actually attach to, not `Category` directly, so the same category can have per-budget allocations and per-budget transaction linkage.
- `Transaction` belongs to a `Budget` (required) and optionally an `Account` and a `BudgetCategory`.
- `Account` belongs to an `AppUser` and holds a running `balance`.

### Controller styles

Two distinct controller styles coexist:
- `@Controller` MVC controllers (`BudgetController`, `AppUserController`) render Thymeleaf templates and return view names/redirects. `BudgetController` distinguishes full-page loads from htmx partial requests via the `HX-Request` header, returning a Thymeleaf fragment (`fragments/budget-plan :: budget-plan`) for the latter.
- `@RestController` JSON APIs (`AccountController`, `CategoryController`, `GroupController`, `TransactionController`) return DTOs/`ResponseEntity`.

REST controllers validate request bodies with `@Valid` + `BindingResult` and manually build a field→message error map on validation failure (400), rather than relying solely on `GlobalExceptionHandler`.

### Authorization and ownership pattern

There's no per-request "current user" injection via `@AuthenticationPrincipal`; services instead call `AppUserService.getAuthenticatedUser()` (reads `SecurityContextHolder`) to resolve the caller, then scope all repository lookups to that user's ID (e.g. `budgetRepository.findByIdAndAppUserId(...)`, `accountRepository.findByIdAndAppUser(...)`). Resources that don't exist *or* belong to another user both resolve to `404 Not Found` via `ResponseStatusException` — never `403` — to avoid leaking existence of other users' data. Follow this pattern (ownership-scoped queries + 404-on-mismatch) when adding new endpoints.

`GlobalExceptionHandler` only maps `IllegalArgumentException`→400 and `IllegalStateException`→403; most services throw `ResponseStatusException` directly for typed HTTP errors instead.

### Business rule: transaction dates

A `Transaction`'s date must fall within its `Budget`'s `month`/`year`; both `TransactionService.createTransaction` and `updateTransaction` validate this and throw 400 otherwise. Keep this check in sync if the validation logic changes in one method but not the other (currently duplicated across both).

### Security

`SecurityConfig` uses session-based form login (`/login`, `/register` public; `email` is the username parameter; success redirects to `/budgets/`). CSRF is disabled. Roles come from the `Authority` entity (`ROLE_USER` assigned on registration in `AppUserService.saveUser`; `/admin/**` requires `ROLE_ADMIN`). Passwords are BCrypt-hashed.

### Testing conventions

Controller tests are `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` with `MockMvc`, seeding data directly through repositories in a `@BeforeAll` (`TestInstance.Lifecycle.PER_CLASS`) and clearing mutable tables in `@BeforeEach`. Authenticated requests use `@WithMockUser(username = <email>)` since the security principal is keyed by email. Each controller test class covers: happy path, validation failures (field-level `jsonPath` assertions), not-found/cross-user access isolation (expect 404), and unauthenticated access (expect 401 or 302).
