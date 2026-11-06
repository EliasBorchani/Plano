# KMP-to-JS slice: friction notes

PRD step 1 asked: build sign-in, create an event and one date poll with the **whole shared Kotlin layer (data and
view models) compiled to JavaScript** and React for the UI only, and report the friction (bundle size, interop,
tooling). Measured on 2026-11-05 with Kotlin 2.4.21, Ktor 3.6.1, coroutines 1.11.0, serialization 1.11.0,
kotlinx-datetime 0.8.0, Koin 4.2.2, lifecycle-viewmodel 2.11.0, React 19.3, Vite 8.3 (Rolldown).

## Verdict: go, with one condition on bundle size

The model works end to end: React screens hold no rule, the same Kotlin code validates events in the browser and
on the server, view model tests run on the JVM and in Node, and the generated TypeScript types are usable. Every
interop problem found had a small, local fix (below). **The cost is size: the Kotlin layer adds about 312 KB
gzipped** to a 94 KB React baseline (283 KB with the ES2015 output, see below). For invitees who open the link in a messaging app on a phone, that has to be
measured on real devices in step 2 (in-app browser test) before the V1 loop is built. If it is too slow, the first
lever is replacing Ktor and kotlinx-datetime (about a quarter of the Kotlin code, see below).

## Bundle size

`npm run build && npm run size` (`apps/web/scripts/size.mjs`). The baseline is React + React DOM + React Router with
one route, built with the same Vite config.

|  | raw | gzip | brotli |
| --- | ---: | ---: | ---: |
| App bundle (all JS) | 1,483 KB | 407 KB | 329 KB |
| React-only baseline | 303 KB | 94 KB | 82 KB |
| **Cost of the Kotlin layer** | **1,181 KB** | **312 KB** | **247 KB** |

The Kotlin output before minification is 2.9 MB. Split by library, from its source map (share of the 1.77 MB the map
attributes to a source):

| Origin | Unminified | Share |
| --- | ---: | ---: |
| kotlin-stdlib | 909 KB | 51 % |
| Ktor client (core, http, io, utils, websockets, content negotiation) | 286 KB | 16 % |
| kotlinx-serialization | 181 KB | 10 % |
| kotlinx-coroutines | 175 KB | 10 % |
| kotlinx-datetime | 105 KB | 6 % |
| Plano's own code (all modules) | 40 KB | 2 % |
| kotlinx-io | 39 KB | 2 % |
| Koin | 23 KB | 1 % |
| atomicfu, stately, lifecycle-viewmodel | 12 KB | < 1 % |

Plus `@js-joda/core`, which kotlinx-datetime needs at runtime (counted in the app bundle, not in this table).

What this means:

- Our code is 2 %: the cost is the runtime and the libraries, so it will not grow much with features.
- Unused code is already removed: compiling the umbrella as an `executable` instead of a `library` gives the exact
  same output (2,994,597 bytes). There is no free win there.
- Levers, by expected gain (not done in the slice): replace Ktor with a small `expect/actual` HTTP function over
  `fetch` (about 18 % with kotlinx-io); drop kotlinx-datetime and js-joda for ISO strings plus a few date helpers
  (about 6 % + js-joda); manual DI instead of Koin (1 %). The stdlib share is the floor.
- **ES2015 output** (`compilerOptions.target.set("es2015")` in `bridge-web`, done after the numbers above): Kotlin
  emits real JS classes instead of prototype functions. Kotlin output 2.99 MB → 2.55 MB; cost of the Kotlin layer
  1,181 → 997 KB raw, **312 → 283 KB gzip** (−9 %). Tests and the browser flow unchanged.
- Not measured yet: parse and run time of the 1.5 MB minified bundle on a low-end phone. That is part of step 2.

## Interop

What worked well:

- **TypeScript types** (`generateTypeScriptDefinitions()`): accurate for data classes, nullable fields
  (`Nullable<T>`), enums (classes with static entries, compared by identity:
  `state.status === SessionStatus.SIGNED_IN`), sealed events (`event instanceof CreateEventEvent.Created`), and a
  generic exported base class (`Controller<S>`).
- **`@JsExport` in common code** on feature state classes: ignored on the JVM, so the same classes serve every
  platform. Exported data classes also expose `copy`, `equals`, `hashCode`, `toString`: noise in the types, harmless.
- **Coroutines and `viewModelScope` in the browser**: `Dispatchers.Main` works; clearing a `ViewModelStore` cancels
  the scope. No extra setup.
- **React binding**: `StateFlow.value` is a stable snapshot, so `useSyncExternalStore` needs no adapter beyond a
  `subscribe(onChange)` that collects the flow (`bridge-web/.../Controller.kt`, `apps/web/src/shared.ts`).
- **Cookies**: the Ktor `Js` engine uses `fetch`, so the httpOnly session cookie travels with no configuration
  (same origin through the Vite proxy).
- **`TimeZone.currentSystemDefault()`** works in the browser without the js-joda time zone database.

Problems found, each with its fix:

1. **Per-module output breaks the umbrella (the worst one).** With the default output granularity, the `.d.mts`
   declares `SessionStatus`, `SignInState`… but `plano-shared.mjs` does not export them: they stay inside
   `plano-features-signin.mjs`. `tsc` passes; the bundler fails with `MISSING_EXPORT`. Fix:
   `kotlin.js.ir.output.granularity=whole-program` in `gradle.properties` (one file, everything exported).
2. **`file:` dependency does not bring its own dependencies.** The generated `package.json` lists `@js-joda/core`
   and `ws`, but npm does not install the dependencies of a linked folder. Fix: declare `@js-joda/core` (same pinned
   version) in `apps/web`, and `resolve.dedupe: ['@js-joda/core']` in Vite, because the symlink resolves outside
   `apps/web` and its imports would not find `apps/web/node_modules`. A version bump of kotlinx-datetime can change
   the js-joda version: check the generated `package.json` after upgrades.
3. **Ktor imports `ws`** (`import('ws')`) on its Node-only WebSocket path. Rolldown fails on it in dev and in build.
   Fix: alias `ws` to an empty module (`apps/web/src/stubs/ws.ts`).
4. **`KtList.asJsReadonlyArrayView()` is a Proxy** whose `has` trap throws for `'length' in view` (Vitest's
   `toHaveLength` does that; some libraries too). `.map`, `.length`, `.find` work. Fix: `arr()` in `shared.ts`
   copies to a real array (lists are small).
5. **Domain enums are not exported**, so state carries enum names as strings (`type: "DINNER"`,
   `myChoice: "PREFER"`). The boundary loses type safety: a typo in React compiles. Accepted for the slice (the UI
   maps names to French labels anyway, and a test checks every type has one). Option: exported enums in the features.
6. **React StrictMode runs effects twice in dev**: two view models are created, so `restore()` / `join()` calls run
   twice in development (both are idempotent). Creating controllers in an effect, not during render, keeps only
   one alive.
7. **A stray export**: the umbrella's types include `initHook`, leaked by a dependency. Harmless.

## Tooling

| Step | Time (warm Gradle daemon, M-series Mac) |
| --- | ---: |
| No-op rebuild of the JS library | 0.8 s |
| One feature file changed → production library | 4.8 s |
| Same, development (unminified) library | 6.6 s |
| `clean` + build, build cache off | 7.8 s |
| `allTests` (JVM + Node), warm | 7.2 s |
| `vite build` | 0.3 s |

- **Dev loop**: a Kotlin change means `npm run shared` (about 5 s) and a page reload; React state is lost. React-only
  changes keep Vite's hot reload. `./gradlew -t :bridge-web:jsBrowserProductionLibraryDistribution` rebuilds on save.
- **Repositories**: `RepositoriesMode.FAIL_ON_PROJECT_REPOS` cannot be used: the Kotlin/JS plugin adds its own
  Node.js download repository. The JetBrains lifecycle-viewmodel fork also needs `google()`.
- **Name clashes**: modules sharing a name (`domain:auth:api`, `domain:events:api`) produced the same
  `api-jvm.jar`, and the server distribution failed on duplicates. Fix in `plano.kmp.library`: each JVM jar is named
  after its module path.
- **Tests**: `kotlin-test` + `kotlinx-coroutines-test` run the same view model tests on the JVM and in Node.
  Browser tests were turned off (they need a headless Chrome for little gain). Vitest can import the Kotlin library
  directly (`src/i18n/fr.test.ts`).
- **Debugging**: the library ships a source map; our code maps back to `.kt` files, libraries to the paths of the CI
  machines that built them. Stepping through Kotlin in the browser's devtools was not tried in the slice.

## Sign-in notes

- **Google**: Google Identity Services in React returns an ID token; the server checks it against Google's keys
  (`aud` = the client ID). Needs an OAuth client ID of type « Web application » with `http://localhost:5173` as an
  authorized origin, in `.env` (`GOOGLE_CLIENT_ID` and `VITE_GOOGLE_CLIENT_ID`). **Not tested with a real Google
  account yet**: no client ID was available during the slice. The server check is the same code as for Apple.
- **Apple**: the code path exists end to end (`apps/web/src/auth/apple.ts`, `JwksIdTokenVerifier.apple`), but Sign
  in with Apple needs a paid Apple Developer account, a Services ID, a verified domain and an HTTPS return URL
  (localhost is refused). Until then, `APP_ENV=dev` swaps in `DevAppleVerifier` (`dev:<name>`), used for the
  two-person poll test. Apple sends the user's name only on the first sign-in, outside the token: the client forwards
  it as `displayName`.
- Not covered by the slice (PRD step 2): Google sign-in inside WhatsApp, Instagram and Messenger in-app browsers,
  where Google is known to refuse embedded web views.
