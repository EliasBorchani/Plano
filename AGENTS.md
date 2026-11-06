# Plano

**Plano** is a temporary workspace per event, opened from one shared link: a friend group agrees on a date and a
place without cluttering their group chat. Shared Kotlin Multiplatform layer (data, rules, view models) with a native
UI per platform: React on the web now, Jetpack Compose and SwiftUI later. Backend: Ktor (Kotlin, JVM) and PostgreSQL.

Product spec: the owner's Claude Doc "PRD – Event Workspace App" (`MUgcrHBGBU3p82YBicLpuC`, readable with the Claude
Docs tools). It is the source of truth for product decisions; when it and this repository disagree, ask.

## Layout

| Folder | What | Rules |
| --- | --- | --- |
| `utilities/` | `kotlin-ext` (`AppError`), `network` (Ktor client factory), `presentation` (`BaseViewModel`) | `docs/architecture.md` |
| `contract/` | Wire DTOs and route paths, shared by the client `:data` modules and the server | `docs/architecture.md` |
| `domain/<ctx>/{api,data}` | Bounded contexts `auth`, `events`, `polls`: models, rules and repository contracts in `api`, Ktor implementations in `data` | `docs/architecture.md` |
| `features/<name>` | View models (MVI) and their exported UI state: `signin`, `events`, `event` | `docs/architecture.md` |
| `bridge-web/` | The web umbrella: one ES module `plano-shared` with TypeScript types, consumed by `apps/web` | `docs/kmp-js-friction.md` |
| `server/` | Ktor backend: sign-in, sessions, events, date poll | `server/AGENTS.md` |
| `apps/web/` | React web app (mobile-first, French) | `apps/web/AGENTS.md` |
| `apps/android/`, `apps/ios/` | later (mobile milestone), consuming the same `features/*` | |
| `build-logic/` | Convention plugins `plano.kmp.library` (JVM + JS targets) and `plano.kmp.feature` | |
| `docs/` | `status.md` (where the project stands), `architecture.md`, `kmp-js-friction.md` (step 1 findings) | |

## Current phase: PRD step 1, the KMP-to-JS slice

Google and Apple sign-in, create an event, one date poll, with the whole shared layer compiled to JavaScript and React
for the UI only. Status and open points: `docs/status.md`. The owner reviews the code before any commit.

## Load-bearing decisions (do not reverse without the owner)

- **Native UI per platform, shared logic.** React only renders and forwards user actions; rules, state and data live
  in Kotlin. No Compose Multiplatform for the UI.
- **The server reuses the shared rules.** It depends on `contract` and `domain/*/api` (enums, `NewEvent.validate()`),
  never on `:data` or features.
- **Features never depend on `:data`.** They see `:api` only; `:data` is wired at the umbrella (`bridge-web` now).
- **Domain modules do not depend on each other.** Cross-context work happens in a view model (`EventViewModel`
  combines events and polls).
- **MVI state is reactive** (`combine(...).stateIn(...)`), as in Tummy. Use cases only when they hold logic.
- **Koin DSL only, no annotations.** Versions only in `gradle/libs.versions.toml`.
- **Visibility comes from event membership only**: a non-member gets 404 on every event route.

## Before saying something works

- `./gradlew allTests`: shared code and view models on JVM and JS (Node).
- `./gradlew :server:test`: needs `docker compose up -d postgres` and a `.env` (copy `.env.example`).
- `cd apps/web && npm run shared && npm test && npm run build`.

## Conventions

- Code-adjacent text in English (identifiers, comments, commit messages). User-facing text in French, only in
  `apps/web/src/i18n/fr.ts`; Kotlin sends keys (enum names), never sentences.
- State exported to JS: `@JsExport` data classes of `String`, `Int`, `Boolean`, `List` and other exported classes.
  No `Long`, no `kotlinx-datetime` types: dates cross as ISO strings.
