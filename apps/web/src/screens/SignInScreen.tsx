import { SignInButtons } from '../auth/SignInButtons'
import { t } from '../i18n/fr'

export function SignInScreen({ onBack }: { onBack?: () => void }) {
  return (
    <main className="page signin">
      <div className="signin-hero">
        <h1 className="brand-large">{t.appName}</h1>
        <p className="muted">{t.tagline}</p>
      </div>

      <section className="card stack">
        <SignInButtons />
      </section>

      {onBack && (
        <button className="button ghost" onClick={onBack}>
          ← {t.back}
        </button>
      )}
    </main>
  )
}
