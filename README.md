# orderflow-api
An order management REST service built with Spring Boot: products, inventory, orders with a status workflow, and JWT auth.

**Goal:** show Java backend skills in a production-style setup. Spring Boot 3 (Java 21), Spring Security with JWT and role-based access, Spring Data JPA on PostgreSQL, Flyway migrations, validation, error handling, OpenAPI docs, JUnit 5 + Mockito + Testcontainers, Docker Compose, GitHub Actions CI.

**Scope (v1):**
- Auth: register and login, with USER and ADMIN roles.
- Products and inventory: CRUD, with optimistic locking on stock.
- Orders: place and cancel orders. Status moves PENDING > PAID > SHIPPED, and the transitions are transactional.
- API: pagination and filtering, idempotency keys on order creation, and a Swagger UI.
- Docs: a static OpenAPI docs page on GitHub Pages.

Status: scaffolding. Roadmap in issues.
