# Status

For a new session: read `AGENTS.md` (root) first, then the `AGENTS.md` of the folder you work in. This file holds
what they do not: where the project stands, decisions taken along the way, known limits and next steps.

## Where we are (2026-11-25)

PRD step 1, the KMP-to-JS slice, then access and refresh tokens: committed and pushed (`main`). Now **guest
voting**: a name is enough to take part (PRD decision).

- A guest is an `app_user` with no `auth_identity`, **bound to one event** (`guest_event_id`, owner's choice), with
  the same tokens as an account. Same rights as members (friend-group default role: planner), but cannot create
  events. Names are unique per event (shared rule `GuestName`, case and spaces ignored).
- The event link, without a session for that event, shows the guest entry: type a name, or « C'est moi » on an
  existing guest (owner: lets a guest come back from another device; no secret, so anyone with the link can do it),
  or « J'ai un compte ». A guest of event A opening event B's link gets B's guest entry; joining B ends the A
  session on this browser (A stays reachable through « C'est moi »).
- After a guest's first vote, the event page offers Google or Apple sign-in (« Garde tes votes »). A new identity
  turns the guest into an account (same id, keeps the name); a known one merges the guest into it, and **the
  account's vote wins** where both voted on the same date (owner).
- Signing out as a guest asks for confirmation first (owner). Guest sessions keep the 30-day refresh (owner).
- Tests: server `GuestTest` (6); `GuestEntryViewModelTest` (4) and 2 new `EventViewModelTest` cases, JVM and Node.
- Checked in headless Chromium (390 px): Alice creates an event; « Bob » joins as a guest (« alice » refused as
  taken), votes, sees the prompt, has no « Nouvel événement »; a second browser continues as Bob and sees his vote;
  Bob signs in (dev Apple) and keeps his name and vote, can now create; a guest's sign-out shows the warning and
  returns to the guest entry. Only expected 401/409 in the console. Once out of ten runs the script read Bob's vote
  as not selected right after signing in; not reproduced in the nine runs after.

Tokens (previous step): `AccessTokens` (15 min JWT), `RefreshTokens` (rotation, 30 s grace, reuse revokes the
family), client refresh on a 401 (`utilities/network/TokenRefresh.kt`). Details in `server/AGENTS.md`.

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
- Joining: an account that opens `/e/<id>` joins as planner (friend-group default); without an account, as a guest.
  Invitations and groups come with the data model step.
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
- No deadline, validation of the winning date, RSVP, notifications, place poll, groups.
- Guests: anyone can create many guests on a link they hold (no rate limit). Native apps cannot merge a guest at
  sign-in yet (the server reads the guest from the web refresh cookie only).
- No lint or formatting tool wired yet (Tummy uses Spotless with ktlint).

## Next steps

1. Owner review of guest voting (committed and pushed, the owner allowed it on 2026-11-25).
2. PRD step 3, the link library in shared Kotlin (a new context in `domain/`).
3. Google client ID: the owner wants to be guided through it when it matters (first deployment).
