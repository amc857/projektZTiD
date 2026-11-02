import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import api from '../api/axios'

/** Redirects to /login if the session is not authenticated. */
export default function PrivateRoute({ children }) {
  const [status, setStatus] = useState('loading') // 'loading' | 'ok' | 'unauth'

  useEffect(() => {
    api.get('/auth/me')
      .then(() => setStatus('ok'))
      .catch(() => setStatus('unauth'))
  }, [])

  if (status === 'loading') return <p>Loading…</p>
  if (status === 'unauth') return <Navigate to="/login" replace />
  return children
}
