import type { FormEvent } from 'react'
import { errorText, eventTypes, t } from '../i18n/fr'
import { arr, guestEntryController, useController } from '../shared'

/** An event link opened without a session for this event: a name is enough to take part. */
export function GuestEntryScreen(props: { eventId: string; currentGuestName?: string; onHaveAccount: () => void }) {
  const { eventId, currentGuestName, onHaveAccount } = props
  const [state, controller] = useController(() => guestEntryController(eventId), [eventId])

  if (!state || !controller || state.loading) return <main className="page center muted">{t.loading}</main>
  if (state.notFound) return <main className="page center">{t.notFound}</main>
  if (!state.eventTitle) {
    return (
      <main className="page stack">
        <p className="error">{errorText(state.error)}</p>
        <button className="button ghost" onClick={() => controller.retry()}>
          {t.retry}
        </button>
      </main>
    )
  }

  const guests = arr(state.guests)

  function join(event: FormEvent) {
    event.preventDefault()
    controller!.join()
  }

  return (
    <main className="page stack">
      <header className="stack small-gap">
        <span className="eyebrow">{eventTypes[state.eventType] ?? state.eventType}</span>
        <h1>{state.eventTitle}</h1>
        <p className="muted">{t.guestIntro}</p>
      </header>

      {currentGuestName && <p className="hint">{t.guestElsewhere(currentGuestName)}</p>}

      <form className="card stack small-gap" onSubmit={join}>
        <label className="field">
          <span className="field-label">{t.yourName}</span>
          <input
            value={state.name}
            onChange={(event) => controller.setName(event.target.value)}
            autoComplete="given-name"
            autoFocus
          />
        </label>
        <button className="button" disabled={state.busy || !state.name.trim()}>
          {t.guestJoin}
        </button>
        {state.error && (
          <p className="error" role="alert">
            {errorText(state.error)}
          </p>
        )}
      </form>

      {guests.length > 0 && (
        <section className="stack small-gap" aria-labelledby="already">
          <h2 id="already">{t.guestAlreadyHere}</h2>
          <ul className="chips">
            {guests.map((guest) => (
              <li key={guest.id}>
                <button className="chip chip-button" disabled={state.busy} onClick={() => controller.continueAs(guest.id)}>
                  {t.itsMe(guest.displayName)}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <button className="button ghost" onClick={onHaveAccount}>
        {t.haveAccount}
      </button>
    </main>
  )
}
