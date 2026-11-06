import { Link, Outlet } from 'react-router'
import { t } from './i18n/fr'
import { SignInScreen } from './screens/SignInScreen'
import { useSession } from './session'
import { SessionStatus, useControllerState } from './shared'

/** Every route needs an account: signed out, any URL (an event link too) shows sign-in, then the page itself. */
export function Root() {
  const session = useSession()
  const state = useControllerState(session)

  if (state.status === SessionStatus.UNKNOWN) {
    return <main className="page center muted">{t.loading}</main>
  }
  if (state.status === SessionStatus.SIGNED_OUT) {
    return <SignInScreen />
  }
  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">
          {t.appName}
        </Link>
        <span className="topbar-user muted">{state.user?.displayName}</span>
        <button className="button ghost small" onClick={() => session.signOut()} disabled={state.busy}>
          {t.signOut}
        </button>
      </header>
      <Outlet />
    </div>
  )
}
