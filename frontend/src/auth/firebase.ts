import { initializeApp } from 'firebase/app'
import { GoogleAuthProvider, getAuth, signInWithPopup, signOut } from 'firebase/auth'

// Public web-app identifiers (not secrets): access is enforced by the backend, which
// verifies every ID token and only accepts verified @gmail.com accounts.
const app = initializeApp({
  apiKey: 'AIzaSyCY5pHbr8d4w-vWZiAk3fMhuZ_WS9dl7IM',
  authDomain: 'react-java-test.firebaseapp.com',
  projectId: 'react-java-test',
  appId: '1:548787529732:web:89f5eee2356b478de52d51',
  messagingSenderId: '548787529732',
})

export const auth = getAuth(app)

export const ALLOWED_DOMAIN = 'gmail.com'

export const isAllowedEmail = (email: string | null | undefined) =>
  !!email && email.toLowerCase().endsWith(`@${ALLOWED_DOMAIN}`)

export async function signInWithGoogle() {
  const provider = new GoogleAuthProvider()
  provider.setCustomParameters({ prompt: 'select_account' })
  const result = await signInWithPopup(auth, provider)
  if (!isAllowedEmail(result.user.email)) {
    await signOut(auth)
    throw new Error(`Solo se admiten cuentas @${ALLOWED_DOMAIN}. Has entrado con ${result.user.email}.`)
  }
  return result.user
}

export const logout = () => signOut(auth)

/** fetch that sends the current user's Firebase ID token (refreshed automatically when expired). */
export async function authFetch(input: string, init: RequestInit = {}) {
  const token = await auth.currentUser?.getIdToken()
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(input, { ...init, headers })
}
