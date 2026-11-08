import type { User } from 'firebase/auth'
import { createContext, useContext } from 'react'

export interface Session {
  user: User
  admin: boolean
}

export const SessionContext = createContext<Session | null>(null)

/** The signed-in user; only available inside AuthGate. */
export const useSession = () => {
  const session = useContext(SessionContext)
  if (!session) throw new Error('useSession fuera de AuthGate')
  return session
}
