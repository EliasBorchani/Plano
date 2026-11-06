import { loadScript } from './script'

// Sign in with Apple JS. Needs a Services ID and an HTTPS domain registered with Apple:
// without VITE_APPLE_CLIENT_ID the sign-in screen offers the dev stand-in instead.
type AppleSignInResponse = {
  authorization: { id_token: string }
  user?: { name?: { firstName?: string; lastName?: string } }
}

declare global {
  interface Window {
    AppleID?: {
      auth: {
        init(options: { clientId: string; scope: string; redirectURI: string; usePopup: boolean }): void
        signIn(): Promise<AppleSignInResponse>
      }
    }
  }
}

export const appleClientId = import.meta.env.VITE_APPLE_CLIENT_ID?.trim() || null

export async function signInWithApple(clientId: string): Promise<{ idToken: string; displayName: string | null }> {
  await loadScript('https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/fr_FR/appleid.auth.js')
  const auth = window.AppleID!.auth
  auth.init({ clientId, scope: 'name email', redirectURI: `${location.origin}/`, usePopup: true })
  const response = await auth.signIn()
  // Apple sends the name only on the very first sign-in, and outside the token.
  const name = response.user?.name
  const displayName = [name?.firstName, name?.lastName].filter(Boolean).join(' ') || null
  return { idToken: response.authorization.id_token, displayName }
}
