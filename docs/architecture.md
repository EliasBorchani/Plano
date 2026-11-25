# Architecture

How the Kotlin code is split and the rules that keep it coherent. Adapted from Tummy's architecture: same module
shapes, plus a web umbrella and a server that share the rules.

## Guiding principles

1. **Native UI, shared logic.** React now, Compose and SwiftUI later. Only non-UI code is shared: state, rules, use
   cases, repositories, wire format.
2. **Bounded contexts drive the module graph.** Each business area is a `domain/<context>` subtree with its own
   `api` and `data` modules. Features compose contexts; they never reach into a context's internals.
3. **No god modules.** No `shared` or `core` module that aggregates everything. Growth happens by adding contexts or
   features.
4. **Contracts in `api`, implementations in `data`.** Models, rules and repository interfaces live in `api`; Ktor
   calls, DTO mapping and Koin modules live in `data`.
5. **Domain modules are independent of each other.** Cross-context logic lives in a view model.
6. **One set of rules, everywhere.** The server runs the same `api` code (validation, enums) as every client.

## Module graph

```
utilities/
  kotlin-ext/       AppError: the one failure type that reaches view models
  network/          Ktor client factory (Js engine in the browser, CIO on the JVM), maps HTTP errors to AppError,
                    refreshes tokens on a 401 and retries once (TokenRefresh, SessionEvents)
  presentation/     BaseViewModel (MVI), on the JetBrains lifecycle-viewmodel fork

contract/           @Serializable DTOs + Routes; enum fields as names (strings), so no domain type leaks in

domain/
  auth/   api/      User (guest or account), AuthProvider, Session, GuestName (shared rule), GuestInvite, AuthRepository
          data/     HttpAuthRepository (tokens = httpOnly cookies), authDataModule
  events/ api/      EventType (27), Role, Event, NewEvent + validate(), EventRepository
          data/     HttpEventRepository (in-memory cache + refresh), eventsDataModule
  polls/  api/      VoteChoice, DatePoll, PollTally (pure), PollRepository
          data/     HttpPollRepository (mutations return the poll), pollsDataModule

features/
  signin/           SignInViewModel: the app-wide session; GuestEntryViewModel: join an event link with a name
  events/           EventListViewModel, CreateEventViewModel
  event/            EventViewModel: event + date poll (two contexts, combined here)

bridge-web/         web umbrella: @JsExport controllers + startPlano(), one ES module "plano-shared" (+ .d.mts)
server/             Ktor app: depends on contract + domain/*/api only
```

## Dependency rules

| Module | May depend on | Must not depend on |
| --- | --- | --- |
| `utilities:*` | stdlib, kotlinx, one targeted library per module | `contract`, `domain:*`, `features:*` |
| `contract` | kotlinx-serialization | everything else |
| `domain:<ctx>:api` | `utilities:*`, kotlinx | other `domain:*`, any `:data`, `contract`, `features:*` |
| `domain:<ctx>:data` | own `:api`, `contract`, `utilities:*` | other `domain:*`, `features:*` |
| `features:<name>` | `domain:*:api`, `utilities:presentation`, `utilities:kotlin-ext` | any `:data`, `utilities:network`, other features |
| `bridge-web` (later `umbrella`, `apps/android`) | everything: wires `:data` into Koin | — |
| `server` | `contract`, `domain:*:api` | any `:data`, `features:*`, `utilities:network` |

`contract` is the one addition to Tummy's graph: the server and the client `:data` modules must agree on the JSON,
so its shape lives in one place. It names enum values as strings so it never ties two contexts together.

## Presentation (MVI)

- `BaseViewModel<State, Intent, Event>`: `state` is a `StateFlow` built with `combine(...).stateIn(...)`, never a
  mutable reducer; imperative bits (a text field) are private `MutableStateFlow`s that flow into the combine.
  `onIntent(intent)` takes user input; `events` carries one-shot actions (navigate after creating).
- State classes are what a UI shows: `@JsExport` data classes of strings, numbers, booleans and lists. Enums cross as
  their names (`"PREFER"`) and the UI maps them to its own labels.
- Errors reach state as an `AppError.Kind` name (`NETWORK`, `NOT_FOUND`) or a rule's error name (`DATE_IN_PAST`).
- View models take their dependencies through Koin factories (`<feature>FeatureModule`), with parameters for ids
  (`parametersOf(eventId, joinOnOpen)`).

## Platform adapters

- **Web (`bridge-web`)**: each view model is wrapped in an exported `Controller<S>`: `state`, `subscribe(onChange)`
  (the shape React's `useSyncExternalStore` wants), `dispose()` (clears its `ViewModelStore`, which cancels
  `viewModelScope`), and one method per intent with plain arguments. `startPlano(apiBaseUrl)` starts Koin once.
- **Android (later)**: `koinViewModel()` in Compose, as in Tummy.
- **iOS (later)**: an `umbrella` framework with SKIE, as in Tummy; the `features` stay unchanged.

## Targets

`plano.kmp.library` compiles every shared module for `jvm()` (server and fast tests) and `js()` (browser, tests in
Node). Android and iOS targets are added there at the mobile milestone.
