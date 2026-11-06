# Plano

One temporary workspace per event, opened from a single shared link: someone drops the link in the group chat,
friends tap it and agree on a date (and soon a place) there, instead of in the chat.

Status: step 1 of the PRD, a thin slice to test the stack (sign-in, create an event, one date poll).

## How it fits together

```
apps/web (React) ── imports ── plano-shared (Kotlin compiled to JS: view models, rules, Ktor client)
                                     │  HTTPS, token cookies
                                     ▼
                              server (Ktor, JVM) ── PostgreSQL
                                     │
                 reuses contract/ and domain/*/api (the same Kotlin rules as the clients)
```

Android (Compose) and iOS (SwiftUI) come later and will use the same `features/*` view models.

| Folder | What |
| --- | --- |
| `utilities/`, `contract/`, `domain/`, `features/` | The shared Kotlin Multiplatform layer (JVM + JS targets) |
| `bridge-web/` | Packs the shared layer into one JS module with TypeScript types |
| `server/` | The backend (Ktor, PostgreSQL, Flyway) |
| `apps/web/` | The web app (React, TypeScript, Vite), in French |
| `docs/` | Status, architecture, and the KMP-to-JS friction notes |

## Quick start

Needs Docker, JDK 21 and Node 22+.

```sh
cp .env.example .env                      # set POSTGRES_PASSWORD; GOOGLE_CLIENT_ID for Google sign-in
docker compose up -d postgres             # PostgreSQL on 127.0.0.1:5434
./gradlew :server:run                     # the API on 127.0.0.1:8080 (reads .env)

cd apps/web
npm install
npm run shared                            # compiles the Kotlin layer to JS (rerun after Kotlin changes)
npm run dev                               # http://localhost:5173
```

Without a Google client ID or an Apple Developer account, `APP_ENV=dev` offers « Apple (développement) »: a first name
is enough to sign in, so two browser profiles can test a poll together.

The full stack in Docker: `docker compose up -d --build` (the web app is not containerized yet).

## Tests

| Part | Command |
| --- | --- |
| Shared Kotlin (JVM and Node) | `./gradlew allTests` |
| Server (needs the compose Postgres) | `./gradlew :server:test` |
| Web app | `cd apps/web && npm test && npm run build` |
| Bundle size report | `cd apps/web && npm run build && npm run size` |

## For coding agents

Start with `AGENTS.md`, then the `AGENTS.md` of the folder you work in. `CLAUDE.md` files import them.
