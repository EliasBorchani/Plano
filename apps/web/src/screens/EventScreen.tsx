import { useState, type FormEvent } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { formatDate, todayIso } from '../format'
import { choices, errorText, eventTypes, roles, t } from '../i18n/fr'
import { arr, eventController, useController, type DateOptionUi, type EventController } from '../shared'

export function EventScreen() {
  const { eventId = '' } = useParams()
  // Opened from inside the app: already a member. Opened from a shared link: join first.
  const fromApp = (useLocation().state as { fromApp?: boolean } | null)?.fromApp === true
  const [state, controller] = useController(() => eventController(eventId, !fromApp), [eventId])

  if (!state || !controller || state.loading) return <main className="page muted">{t.loading}</main>
  if (state.notFound) {
    return (
      <main className="page stack">
        <p className="card">{t.notFound}</p>
        <Link to="/">← {t.myEvents}</Link>
      </main>
    )
  }
  if (!state.title) {
    return (
      <main className="page stack">
        <p className="error">{errorText(state.error)}</p>
        <button className="button ghost" onClick={() => controller.refresh()}>
          {t.retry}
        </button>
      </main>
    )
  }

  const options = arr(state.options)
  const members = arr(state.members)

  function addDate(event: FormEvent) {
    event.preventDefault()
    controller!.addDate()
  }

  return (
    <main className="page stack">
      <Link to="/" className="back">
        ← {t.myEvents}
      </Link>

      <header className="stack small-gap">
        <span className="eyebrow">{eventTypes[state.type] ?? state.type}</span>
        <h1>{state.title}</h1>
        {state.description && <p className="description">{state.description}</p>}
        <ShareButton eventId={eventId} title={state.title} />
      </header>

      <section className="stack small-gap" aria-labelledby="when">
        <h2 id="when">{t.whenTitle}</h2>
        {options.length === 0 ? (
          <p className="card muted">{state.canPlan ? t.noDates : t.noDatesParticipant}</p>
        ) : (
          <ul className="list">
            {options.map((option) => (
              <DateOption
                key={option.id}
                option={option}
                choiceKeys={arr(state.choices)}
                memberCount={members.length}
                busy={state.busy}
                controller={controller}
              />
            ))}
          </ul>
        )}

        {state.canPlan && (
          <form className="add-date" onSubmit={addDate}>
            <label className="field grow">
              <span className="field-label">{t.newDateLabel}</span>
              <input type="date" min={todayIso()} value={state.newDate} onChange={(e) => controller.setNewDate(e.target.value)} />
            </label>
            <button className="button" disabled={state.busy || !state.newDate}>
              {t.addDate}
            </button>
          </form>
        )}
        {state.error && (
          <p className="error" role="alert">
            {errorText(state.error)}
          </p>
        )}
      </section>

      <section className="stack small-gap" aria-labelledby="people">
        <h2 id="people">
          {t.people} <span className="muted">· {members.length}</span>
        </h2>
        <ul className="chips">
          {members.map((member) => (
            <li key={member.userId} className="chip" title={roles[member.role]}>
              {member.displayName}
              {member.isMe && <span className="muted"> ({t.you})</span>}
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}

function DateOption(props: {
  option: DateOptionUi
  choiceKeys: readonly string[]
  memberCount: number
  busy: boolean
  controller: EventController
}) {
  const { option, choiceKeys, memberCount, busy, controller } = props
  const total = Math.max(memberCount, option.prefer + option.ifMajority + option.cannot, 1)
  const counts: Record<string, number> = { PREFER: option.prefer, IF_MAJORITY: option.ifMajority, CANNOT: option.cannot }

  return (
    <li className={`card option${option.leadingOnPrefer || option.leadingWithIfMajority ? ' leading' : ''}`}>
      <div className="row between">
        <span className="option-date">{formatDate(option.date)}</span>
        <LeadBadges option={option} />
      </div>
      <div className="meter" aria-hidden>
        <span className="meter-prefer" style={{ width: `${(option.prefer / total) * 100}%` }} />
        <span className="meter-maybe" style={{ width: `${(option.ifMajority / total) * 100}%` }} />
        <span className="meter-cannot" style={{ width: `${(option.cannot / total) * 100}%` }} />
      </div>
      <div className="choices" role="group" aria-label={formatDate(option.date)}>
        {choiceKeys.map((key) => (
          <button
            key={key}
            className={`choice choice-${key.toLowerCase()}`}
            aria-pressed={option.myChoice === key}
            title={choices[key]?.label}
            disabled={busy}
            onClick={() => controller.vote(option.id, key)}
          >
            <span>{choices[key]?.short ?? key}</span>
            <span className="choice-count">{counts[key] ?? 0}</span>
          </button>
        ))}
      </div>
    </li>
  )
}

/** Two rankings (owner's rule): most « Oui », and most « Oui » + « Si majorité ». One badge when they agree. */
function LeadBadges({ option }: { option: DateOptionUi }) {
  if (option.leadingOnPrefer && option.leadingWithIfMajority) return <span className="badge">{t.leading}</span>
  return (
    <span className="badges">
      {option.leadingOnPrefer && <span className="badge">{t.leadingOnPrefer}</span>}
      {option.leadingWithIfMajority && <span className="badge soft">{t.leadingWithIfMajority}</span>}
    </span>
  )
}

function ShareButton({ eventId, title }: { eventId: string; title: string }) {
  const [copied, setCopied] = useState(false)
  const url = `${location.origin}/e/${eventId}`

  async function share() {
    if (navigator.share) {
      await navigator.share({ title, text: t.shareText(title), url }).catch(() => {})
      return
    }
    await navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <button className="button ghost small share" onClick={share}>
      {copied ? t.copied : t.share}
    </button>
  )
}
