# Web app (`apps/web/`)

The Plano web app, mobile-first, in French. React draws the screens; state, rules and server calls are the shared
Kotlin layer, imported as the npm package `plano-shared` (built by `bridge-web`).

## Build and test

Build the Kotlin layer first: `npm run shared` (Gradle, about 5 s after a Kotlin change; reload the page after it).
Then `npm install`, `npm run dev` (port 5173, `/api` proxied to the server on 8080), `npm test`, `npm run build`,
`npm run size` (bundle report, after a build).

## How it works

- React 19, React Router 8, Vite 8, TypeScript 6, Vitest. Env comes from the repository root `.env`
  (`envDir`): `VITE_GOOGLE_CLIENT_ID`, `VITE_APPLE_CLIENT_ID`.
- `src/shared.ts` is the only file that knows the Kotlin shapes: it re-exports `plano-shared`, gives `arr()` (Kotlin
  lists to arrays) and the hooks. `useController(create, deps)` creates a controller in an effect, disposes it on
  unmount, and reads its state with `useSyncExternalStore`. `useControllerState` is for the app-wide session.
- `startPlano(apiBaseUrl)` runs once in `main.tsx`; the session controller lives for the whole page
  (`SessionContext`). `Root.tsx` shows sign-in on any URL while signed out, so an event link works before sign-in.
- Event links: `/e/<id>`. Opened from inside the app (`state.fromApp`), the page loads the event; opened from a
  shared link, it joins first.
- Sign-in: Google Identity Services (`src/auth/google.ts`) hands an ID token to `signInWithGoogle`; Sign in with
  Apple JS (`src/auth/apple.ts`) when `VITE_APPLE_CLIENT_ID` is set, else the dev form (`dev:<name>`).
- All French text in `src/i18n/fr.ts`. Kotlin sends keys (enum names); `src/i18n/fr.test.ts` runs the Kotlin layer and
  fails when a type has no label. Dates: `src/format.ts`.
- Vite config workarounds, each explained in `vite.config.ts` and `docs/kmp-js-friction.md`: `resolve.dedupe` for
  `@js-joda/core` (a dependency of kotlinx-datetime, declared here because npm does not install the dependencies of a
  `file:` link) and an alias stubbing `ws` (Ktor's Node-only WebSocket path).

## Rules

- Screens import from `./shared`, never from `plano-shared` directly.
- No business rule in React: if a screen needs to decide something, it belongs in a view model.
- 44 px touch targets, one column; check at 360 px.
