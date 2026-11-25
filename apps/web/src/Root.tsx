import { useState } from 'react'
import { Link, Outlet, useMatch } from 'react-router'
import { t } from './i18n/fr'
import { GuestEntryScreen } from './screens/GuestEntryScreen'
import { SignInScreen } from './screens/SignInScreen'
import { useSession } from './session'
import { SessionStatus, useControllerState } from './shared'

/**
 * An event link works without an account: a name is enough (guest entry). Every other page needs a session;
 * signed out, it shows sign-in, then the page itself.
 */
export function Root() {
  const session = useSession()
  const state = useControllerState(session)
  const eventId = useMatch('/e/:eventId')?.params.eventId
  const [wantsAccount, setWantsAccount] = useState(false)

  if (state.status === SessionStatus.UNKNOWN) {
    return <main className="page center muted">{t.loading}</main>
  }
  if (eventId && state.needsGuestEntry(eventId) && !wantsAccount) {
    return (
      <GuestEntryScreen
        eventId={eventId}
        currentGuestName={state.user?.isGuest ? state.user.displayName : undefined}
        onHaveAccount={() => setWantsAccount(true)}
      />
    )
  }
  if (state.status === SessionStatus.SIGNED_OUT || (eventId && state.needsGuestEntry(eventId))) {
    return <SignInScreen onBack={eventId ? () => setWantsAccount(false) : undefined} />
  }

  function signOut() {
    // A guest has no account to come back to: only « C'est moi » on the event link.
    if (state.user?.isGuest && !confirm(t.guestSignOutWarning)) return
    session.signOut()
  }

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">
          {t.appName}
        </Link>
        <span className="topbar-user muted">{state.user?.displayName}</span>
        <button className="button ghost small" onClick={signOut} disabled={state.busy}>
          {t.signOut}
        </button>
      </header>
      <Outlet />
    </div>
  )
}
