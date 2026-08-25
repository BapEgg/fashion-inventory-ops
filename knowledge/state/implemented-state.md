# Implemented State

Last verified: 2026-08-25

## Present in the repository

- Portfolio-oriented README with product, user, workflow, architecture, ERD and local execution
- Java 21, Spring Boot 4.1.0 and Gradle Wrapper 9.5.1 Backend scaffold
  - Web MVC, JPA, Validation, Batch, Spring Boot Flyway Starter, Flyway Oracle and Oracle JDBC
- React 19, TypeScript 5.9 and Vite 8 Frontend scaffold
- Oracle Database Free 23ai Docker Compose service
  - Pinned image: `gvenzl/oracle-free:23.26.2-slim-faststart`
  - Local-only port binding, health check and persistent Docker Volume
- One ignored root `.env` for Oracle and optional LLM settings, with random-password setup command
- Approved domain ERD and versioned Oracle migrations
  - `V1`: domain schema, constraints and indexes
  - `V2`: synthetic Golden Scenario Seed
  - `V3`: Spring Batch 6.0.4 Oracle metadata schema
  - `V4`: concise Korean comments for all domain tables and columns
- README-rendered SVG architecture and ERD with editable draw.io sources
- CSV Seed validation for headers, keys, references, quantities and Golden Scenario expectations
- Approved MVP specification, business assumptions, data pipeline and short agent handoff skills

## Not implemented

- JPA domain entities and repositories
- Deterministic inventory calculation code and feature-level unit tests
- Spring Batch analysis job
- REST APIs
- Inventory exception and simulation screens
- Decision persistence behavior
- LLM provider integration

## Environment observations

- Java: Temurin 21.0.11
- Gradle: committed Wrapper 9.5.1
- Docker Engine: 29.6.2; Docker Compose: 5.3.1
- Oracle: 23.26.2 in `stockpilot-oracle-1`, healthy on `127.0.0.1:1521/FREEPDB1`
- Node.js/npm: unavailable on the user PATH; the Frontend was verified with the Codex bundled runtime
- LLM provider settings: intentionally empty and not required while AI is disabled

## Validation record

- Seed: `.\\scripts\\local.ps1 seed-check` — passed; products 1, stores 3, inventory 3, sales 21
- Compose: `docker compose --env-file .env.example config --quiet` — passed
- Oracle: `.\\scripts\\local.ps1 db-up` and `db-status` — container healthy
- Flyway and Backend: Backend started against Oracle; four migrations applied; schema at version 4
- Oracle readback: products 1, stores 3, inventory 3, sales 21, successful migrations 4
- Oracle comments: eight domain table comments and 54 domain column comments
- Diagram sources: both SVG and draw.io XML files parsed successfully; SVG layouts rendered for visual inspection
- Backend test: Gradle `test` — passed
- Frontend: TypeScript compile and Vite production build — passed

The schema and Seed are working infrastructure. Batch analysis, APIs and UI features must not be presented as implemented.
