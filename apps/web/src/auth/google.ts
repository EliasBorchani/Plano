import { loadScript } from './script'

// Google Identity Services: the button gives an ID token (a JWT) that the server verifies.
type CredentialResponse = { credential: string }

type GoogleAccounts = {
  id: {
    initialize(options: { client_id: string; callback: (response: CredentialResponse) => void; ux_mode?: 'popup' }): void
    renderButton(parent: HTMLElement, options: Record<string, unknown>): void
  }
}

declare global {
  interface Window {
    google?: { accounts: GoogleAccounts }
  }
}

export const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() || null

export async function renderGoogleButton(parent: HTMLElement, clientId: string, onIdToken: (idToken: string) => void) {
  await loadScript('https://accounts.google.com/gsi/client')
  const accounts = window.google!.accounts
  accounts.id.initialize({ client_id: clientId, callback: (response) => onIdToken(response.credential), ux_mode: 'popup' })
  accounts.id.renderButton(parent, {
    theme: 'outline',
    size: 'large',
    shape: 'pill',
    text: 'continue_with',
    locale: 'fr',
    width: Math.min(parent.clientWidth || 320, 400),
  })
}
