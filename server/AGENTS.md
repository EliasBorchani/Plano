# Server (`server/`)

The Ktor backend (JVM). It stores accounts, sessions, events and the date poll, and checks every rule again
with the shared Kotlin code (`contract`, `domain/*/api`) the clients run.

## Build and test

- From the repository root, with `.env` (copy `.env.example`) and `docker compose up -d postgres` (host port 5434):
  `./gradlew :server:run` (port 8080) and `./gradlew :server:test`. Both tasks read the root `.env`.
- Each test gets its own temporary database on the compose Postgres (`TestDatabase`), dropped afterwards.
- Docker: `docker compose up -d --build`; the image is built from the repository root (`server/Dockerfile`).

## How it works

- Stack: Ktor (Netty), plain JDBC through HikariCP (`Db.kt`: `tx {}`, `query`, `update`), Flyway migrations in
  `src/main/resources/db/migration/`, kotlinx.serialization, nimbus-jose-jwt.
- Sign-in (`auth/`): `POST /auth/{google|apple} {idToken, displayName?}`. The provider's ID token is checked against
  its JWKS (`JwksIdTokenVerifier`: signature, `aud`, `iss`, `exp`), then the account is found or created through
  `auth_identity(provider, subject)`. A session is a random token in an httpOnly `plano_session` cookie
  (SameSite=Lax, 30 days); only its SHA-256 is stored. `GET /me`, `POST /auth/logout`.
- Providers come from the environment (`Main.kt`, `verifiersFor`): Google when `GOOGLE_CLIENT_ID` is set; Apple when
  `APPLE_SERVICES_ID` is set, else `DevAppleVerifier` (accepts `dev:<name>`) only when `APP_ENV=dev`.
- Events (`events/`): `GET /events`, `POST /events` (validated with the shared `NewEvent.validate()`; creates the
  owner membership and the date poll), `GET /events/{id}`, `POST /events/{id}/join` (slice rule: anyone signed in
  with the link joins as planner; invitations come later).
- Date poll (`polls/`): `GET /events/{id}/poll`, `POST …/poll/options {date}` (planners; no past date, a day of
  slack for time zones; a date once per poll → 409), `PUT …/poll/options/{optionId}/vote {choice|null}`. Mutations
  answer with the whole poll.
- Access: `requireRole` gives the caller's role or 404, so a non-member never learns an event exists; 403 only for
  a member who may not do something (a participant adding a date).
- Errors: `ApiError(status, code)` → `{code, message}` JSON (`Errors.kt`). Codes are the shared enum names where one
  exists (`TITLE_MISSING`, `DATE_ALREADY_OFFERED`).

## Rules

- Add every new event route behind `requireRole`, and a test in `ApiTest` calling it as a stranger.
- Migrations are append-only: never edit a released `V*.sql`, add the next one.
- Never store Google content other than a place ID (PRD, Risks).
