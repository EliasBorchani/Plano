# Server (`server/`)

The Ktor backend (JVM). It stores accounts, refresh tokens, events and the date poll, and checks every rule again
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
  `auth_identity(provider, subject)` (`Users`).
- Tokens (`AccessTokens`, `RefreshTokens`): sign-in gives an **access token** (HS256 JWT, `JWT_SECRET`, 15 min,
  never stored, checked without the database) and a **refresh token** (random, only its SHA-256 in
  `refresh_token`, 30 days that restart at each refresh). Web: httpOnly cookies `plano_access` (path `/`) and
  `plano_refresh` (path `<PUBLIC_API_PATH>/auth`, so only the auth routes receive it). Native apps: `Authorization:
  Bearer` and `{refreshToken}` in the body of refresh and logout, answered with `TokenPairDto`.
- `POST /auth/refresh` rotates the token inside its family (one sign-in on one device). A replaced token sent again
  within 30 s gets 401 and nothing else (two tabs refreshing at once); later, it revokes the whole family (theft).
  `POST /auth/logout` revokes the family; its access tokens still work until they expire. `GET /me`.
- **Guests** (`Users`, `V3__guests.sql`): an `app_user` with no `auth_identity` and a `guest_event_id`, bound to
  that one event. Public routes (no session): `GET auth/guest/{eventId}` (title, type, guests), `POST
  auth/guest/{eventId} {displayName}` (new guest, checked with the shared `GuestName`; a name already used by a member
  of the event → 409 `NAME_TAKEN`), `POST auth/guest/{eventId}/as/{guestId}` (« C'est moi »: no secret on purpose,
  owner 2026-11-25, so a guest can come back from another device; bound to one event, so it opens nothing else).
  A guest may not create events (403 `GUEST_CANNOT_CREATE`) nor join another one (403 `GUEST_OF_OTHER_EVENT`).
- Google or Apple sign-in while the browser holds a guest's refresh cookie: a new identity turns the guest into an
  account (same id, keeps the guest's name); a known identity merges the guest into that account (membership, votes,
  added dates; **the account's vote wins** on a date both voted on, owner's rule) and deletes the guest.
- Providers come from the environment (`Main.kt`, `verifiersFor`): Google when `GOOGLE_CLIENT_ID` is set; Apple when
  `APPLE_SERVICES_ID` is set, else `DevAppleVerifier` (accepts `dev:<name>`) only when `APP_ENV=dev`.
- Events (`events/`): `GET /events`, `POST /events` (validated with the shared `NewEvent.validate()`; creates the
  owner membership and the date poll), `GET /events/{id}`, `POST /events/{id}/join` (slice rule: anyone signed in
  with the link joins as planner; invitations come later).
- Date poll (`polls/`): `GET /events/{id}/poll`, `POST …/poll/options {date}` (planners; no past date, a day of
  slack for time zones; a date once per poll → 409), `PUT …/poll/options/{optionId}/vote {choice|null}`. Mutations
  answer with the whole poll.
- Link previews (`links/`): `POST links/preview {text}` (signed in, guests too) → `LinkCardDto`. The shared
  `Links.card` cleans and parses; the server only adds what needs fetching. Google short links: redirects followed on
  Google hosts only, up to 5, never loading a page, until a Google Maps long link appears (EU consent page unwrapped
  by `Links.clean`). Other links with a guessed title: the page's `og:title`/`<title>` and description (jsoup).
  **Never fetch a Google page** (content may not be stored).
- Places (`places/`, `V4__places.sql`): `GET places/search?q=` (OpenStreetMap results, not stored), `POST places
  {osmRef}` (stores the picked one: from our recent search, else one `/lookup`), `POST places/from-link {text}`
  (a Google Maps link: its name searched in a ~550 m box around its position, then its first meaningful word,
  best match by the shared `PlaceMatching`; no match → a `LINK` place from the link's name and position, one per
  canonical link). A place is stored once (`osm_ref` or `link_url` unique) with a copy of name, address, position.
- `Nominatim`: the public server (`NOMINATIM_URL` to change it), one request per second for the whole server
  (queued, `503 SEARCH_UNAVAILABLE` after 8 s or on any failure), results cached 24 h in memory, User-Agent
  `Plano/0.1 (+https://github.com/EliasBorchani/Plano)` (owner, 2026-11-25). OSM places must be shown with
  « © OpenStreetMap contributors » (UI, later).
- `SafeFetcher`: http(s) on ports 80/443, every address checked by `AddressPolicy` (no loopback, private,
  link-local, CGNAT, multicast, ULA) through OkHttp's `Dns`, so the connection uses exactly the checked addresses;
  literal IPs checked before the call; 5 s timeout, 512 KB read, 5 redirects, each hop checked. Generic
  User-Agent. Tests relax the policy and map every host to a local `HttpServer`.
- Access: `requireUser` reads the caller from the access token (401 when missing or expired); `requireRole` gives the caller's role or 404, so a non-member never learns an event exists; 403 only for
  a member who may not do something (a participant adding a date).
- Errors: `ApiError(status, code)` → `{code, message}` JSON (`Errors.kt`). Codes are the shared enum names where one
  exists (`TITLE_MISSING`, `DATE_ALREADY_OFFERED`).

## Rules

- Add every new event route behind `requireRole`, and a test in `ApiTest` calling it as a stranger.
- Migrations are append-only: never edit a released `V*.sql`, add the next one.
- Never store Google content other than a place ID (PRD, Risks).
