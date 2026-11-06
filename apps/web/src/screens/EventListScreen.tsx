import { Link } from 'react-router'
import { errorText, eventTypes, roles, t } from '../i18n/fr'
import { arr, eventListController, useController } from '../shared'

export function EventListScreen() {
  const [state, controller] = useController(() => eventListController(), [])

  return (
    <main className="page stack">
      <div className="row between">
        <h1>{t.myEvents}</h1>
        <Link to="/new" className="button">
          {t.newEvent}
        </Link>
      </div>

      {!state || state.loading ? (
        <p className="muted">{t.loading}</p>
      ) : state.error ? (
        <div className="card stack small-gap">
          <p className="error">{errorText(state.error)}</p>
          <button className="button ghost" onClick={() => controller?.refresh()}>
            {t.retry}
          </button>
        </div>
      ) : arr(state.items).length === 0 ? (
        <p className="card muted">{t.noEvents}</p>
      ) : (
        <ul className="list">
          {arr(state.items).map((item) => (
            <li key={item.id}>
              <Link to={`/e/${item.id}`} state={{ fromApp: true }} className="card event-card">
                <span className="eyebrow">{eventTypes[item.type] ?? item.type}</span>
                <span className="event-title">{item.title}</span>
                <span className="muted small">{roles[item.role]}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
