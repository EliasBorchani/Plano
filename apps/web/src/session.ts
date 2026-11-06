import { createContext, useContext } from 'react'
import type { SessionController } from './shared'

export const SessionContext = createContext<SessionController | null>(null)

export function useSession(): SessionController {
  const session = useContext(SessionContext)
  if (!session) throw new Error('SessionContext missing')
  return session
}
