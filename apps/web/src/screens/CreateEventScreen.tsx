import { useEffect, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { errorText, eventTypes, t } from '../i18n/fr'
import { arr, createEventController, CreateEventEvent, useController } from '../shared'

export function CreateEventScreen() {
  const navigate = useNavigate()
  const [state, controller] = useController(() => createEventController(), [])

  useEffect(
    () =>
      controller?.onEvent((event) => {
        if (event instanceof CreateEventEvent.Created) navigate(`/e/${event.eventId}`, { replace: true, state: { fromApp: true } })
      }),
    [controller, navigate],
  )

  if (!state || !controller) return <main className="page muted">{t.loading}</main>

  const errors = arr(state.errors)
  const titleError = errors.find((e) => e.startsWith('TITLE'))
  const descriptionError = errors.find((e) => e.startsWith('DESCRIPTION'))

  function submit(event: FormEvent) {
    event.preventDefault()
    controller!.submit()
  }

  return (
    <main className="page stack">
      <Link to="/" className="back">
        ← {t.back}
      </Link>
      <h1>{t.newEvent}</h1>

      <form className="card stack" onSubmit={submit} noValidate>
        <label className="field">
          <span className="field-label">{t.title}</span>
          <input
            value={state.title}
            onChange={(e) => controller.setTitle(e.target.value)}
            placeholder={t.titlePlaceholder}
            aria-invalid={!!titleError}
            autoFocus
          />
          {titleError && <span className="error small">{errorText(titleError)}</span>}
        </label>

        <label className="field">
          <span className="field-label">{t.type}</span>
          <select value={state.type} onChange={(e) => controller.setType(e.target.value)}>
            {arr(state.types).map((type) => (
              <option key={type} value={type}>
                {eventTypes[type] ?? type}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span className="field-label">{t.description}</span>
          <textarea
            value={state.description}
            onChange={(e) => controller.setDescription(e.target.value)}
            placeholder={t.descriptionPlaceholder}
            rows={3}
            aria-invalid={!!descriptionError}
          />
          {descriptionError && <span className="error small">{errorText(descriptionError)}</span>}
        </label>

        {state.error && <p className="error">{errorText(state.error)}</p>}
        <button className="button" disabled={state.submitting}>
          {state.submitting ? t.creating : t.create}
        </button>
      </form>
    </main>
  )
}
