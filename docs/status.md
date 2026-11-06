# Status

For a new session: read `AGENTS.md` (root) first, then the `AGENTS.md` of the folder you work in. This file holds
what they do not: where the project stands, decisions taken along the way, known limits and next steps.

## Where we are (2026-11-06)

PRD step 1, the KMP-to-JS slice: committed and pushed (`main`). Since then, **access and refresh tokens**
(not reviewed or committed yet), asked by the owner before guest voting.

- Server: `AccessTokens` (15 min JWT), `RefreshTokens` (rotation, 30 s grace, reuse revokes the family), tested
  with a fake clock in `TokenTest` (10). Details in `server/AGENTS.md`.
- Client: `utilities/network/TokenRefresh.kt` refreshes on a 401 and retries once; parallel 401s share one
  refresh; a failed refresh calls `SessionEvents.onExpired`, and `HttpAuthRepository` goes `SignedOut`.
  Tests: `TokenRefreshTest` (5), `HttpAuthRepositoryTest` (1), on JVM and Node.
- Checked in Chrome with `ACCESS_TTL=20s`: after 25 s, adding a date gave 401 → refresh 204 → retry 200; after
  revoking the refresh tokens in the database, a vote gave 401 → refresh 401 → back to the sign-in screen.
- Bundle: Kotlin cost 285 KB gzip (283 before).

Step 1 itself:

- Shared layer (`utilities`, `contract`, `domain/{auth,events,polls}`, `features/{signin,events,event}`): JVM and JS
  targets. Tests: `PollTally` (5), `HttpPollRepository` with a mock engine (1), `EventViewModel` (4),
  `CreateEventViewModel` (2), each on JVM and Node.
- `bridge-web`: one ES module with TypeScript types.
- `server`: Ktor + PostgreSQL. Tests (6) against temporary databases: sign-in and sessions, dev Apple, shared
  validation, 404 for strangers then join, date poll (duplicate 409, past date 400, vote change and removal).
- `apps/web`: sign-in, event list, create event, event page with the date poll and a share button. Tests (6):
  date formatting, French labels for every Kotlin enum value.
- Checked in Chrome (desktop window, and the event page in a 360 px and a 390 px frame): dev Apple sign-in as
  « Alice », create « Dîner chez Léa » (empty title refused by the shared rule first), add two dates, vote; sign out,
  open the event link as « Bob », join, vote; tally and « En tête » follow the provisional rule. No console errors.
  Only the dark theme was seen (the machine is in dark mode).
- Findings for the PRD: `docs/kmp-js-friction.md` (verdict: go; bundle size is the cost to watch).

## Not verified

- **Google sign-in with a real account**: needs a Google OAuth client ID in `.env` (see `.env.example`).
- **The server Docker image**: Docker Hub was unreachable from this machine (proxy timeout). The Gradle build inside
  the image had reached `installDist` and failed on the duplicate-jar problem, fixed since
  (`./gradlew :server:installDist` passes); the full `docker compose up -d --build` is still to run.
- Real phones and in-app browsers (PRD step 2, skipped by the owner).

## Decisions taken in the slice

- Backend: **Ktor** (owner's choice), so the server reuses `contract` and `domain/*/api`. Plain JDBC (HikariCP),
  Flyway migrations, no ORM.
- Apple: no Apple Developer account yet (owner) → `DevAppleVerifier` behind `APP_ENV=dev`.
- **"Only if it is the majority" (owner, 2026-11-06)**: the poll shows two leaders, the date with the most « Oui »
  and the date with the most « Oui » + « Si majorité ». Each tie goes to fewer « Non », then to the earlier date.
  One badge when both agree. Code: `domain/polls/api/.../PollTally.kt`.
- Joining: in the slice, anyone signed in who opens `/e/<id>` joins as planner (friend-group default). Invitations,
  groups and guests (vote with a name only) come with the data model step.
- Sessions: short access token + rotating refresh token, both in httpOnly cookies on the web (owner, 2026-11-06,
  replaces the single 30-day session token). Same origin (`/api` proxy in dev, `PUBLIC_API_PATH` for the cookie
  path). Logout cannot cancel an access token: it stays valid up to 15 min (accepted by the owner,
  standard practice per RFC 9700). If needed later: an in-memory deny list of revoked families (one instance) or
  a per-request family check in the database.
- Postgres on host port **5434** (5433 is used by dnd-tools' Postgres on this machine).
- Vote choices are shown in enum order: « Oui », « Si majorité », « Non ».
- Share button: keeps the ready-made message with the event name (owner: the whole front will be redone with the
  definitive UI, so no UI polish now).
- PRD step 2 (in-app browser test, phone load time) is skipped (owner). A public HTTPS address comes with the
  server deployment, not before.

## Known limits

- Repository caches are in memory and not cleared on sign-out (each screen refreshes when it opens).
- No realtime: another person's vote shows after a reload or one's own next action.
- No deadline, validation of the winning date, RSVP, notifications, place poll, guests, groups.
- No lint or formatting tool wired yet (Tummy uses Spotless with ktlint).

## Next steps

1. Owner review of the token change; then a commit.
2. Guest voting with a name only (PRD decision): a guest is an `app_user` without `auth_identity`, with the same
   tokens; Google/Apple sign-in after the first vote merges the guest into the account. The dev Apple stand-in is
   not it; it is off in production.
3. Google client ID: the owner wants to be guided through it when it matters (first deployment).
4. PRD step 3, the link library in shared Kotlin (a new context in `domain/`).
