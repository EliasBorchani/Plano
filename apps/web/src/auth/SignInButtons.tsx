import { useEffect, useRef, useState, type FormEvent } from 'react'
import { errorText, t } from '../i18n/fr'
import { useSession } from '../session'
import { useControllerState } from '../shared'
import { appleClientId, signInWithApple } from './apple'
import { googleClientId, renderGoogleButton } from './google'

/** Google and Apple (or the dev Apple form). Used on the sign-in screen and in the guest's « keep your votes » card. */
export function SignInButtons() {
  const session = useSession()
  const state = useControllerState(session)
  const googleButton = useRef<HTMLDivElement>(null)
  const [devName, setDevName] = useState('')
  const [providerError, setProviderError] = useState<string | null>(null)

  useEffect(() => {
    if (!googleClientId || !googleButton.current) return
    renderGoogleButton(googleButton.current, googleClientId, (idToken) => session.signInWithGoogle(idToken)).catch(() =>
      setProviderError(errorText('NETWORK')),
    )
  }, [session])

  async function apple() {
    try {
      const { idToken, displayName } = await signInWithApple(appleClientId!)
      session.signInWithApple(idToken, displayName)
    } catch {
      // Closing Apple's popup lands here too: nothing to show.
    }
  }

  function devApple(event: FormEvent) {
    event.preventDefault()
    const name = devName.trim()
    if (name) session.signInWithApple(`dev:${name}`, name)
  }

  return (
    <div className="stack">
      {googleClientId && <div ref={googleButton} className="google-button" />}

      {appleClientId ? (
        <button className="button apple" onClick={apple} disabled={state.busy}>
          {t.continueWithApple}
        </button>
      ) : (
        <form className="stack small-gap" onSubmit={devApple}>
          <label className="field">
            <span className="field-label">{t.devApple}</span>
            <input
              value={devName}
              onChange={(event) => setDevName(event.target.value)}
              placeholder={t.yourName}
              autoComplete="given-name"
            />
          </label>
          <p className="hint">{t.devAppleHint}</p>
          <button className="button" disabled={state.busy || !devName.trim()}>
            {t.signIn}
          </button>
        </form>
      )}

      {!googleClientId && !import.meta.env.DEV && <p className="hint">{t.noProvider}</p>}
      {(errorText(state.error) ?? providerError) && (
        <p className="error" role="alert">
          {errorText(state.error) ?? providerError}
        </p>
      )}
    </div>
  )
}
