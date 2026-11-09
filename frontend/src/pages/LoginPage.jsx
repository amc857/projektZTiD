import { useState } from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import api from '../api/axios'

// OAuth 2.0: full-page redirect (not XHR) - Spring starts the GitHub flow here
const BACKEND_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080'
const GITHUB_LOGIN_URL = `${BACKEND_URL}/oauth2/authorization/github`

/**
 * Login page.
 * Flow:
 *   1. Submit username + password → POST /api/auth/login
 *   2a. totpRequired=false → navigate to /files
 *   2b. totpRequired=true  → show TOTP code field → POST /api/totp/verify
 *   OR: "Log in with GitHub" → OAuth 2.0 flow handled by Spring → back to /files
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [totpRequired, setTotpRequired] = useState(false)
  const [code, setCode] = useState('')
  // Backend redirects to /login?error=github when the OAuth flow fails
  const [error, setError] = useState(
    searchParams.get('error') === 'github' ? 'GitHub login failed - try again' : ''
  )

  async function handleLogin(e) {
    e.preventDefault()
    setError('')
    try {
      const res = await api.post('/auth/login', { username, password })
      if (res.data.totpRequired) {
        setTotpRequired(true)
      } else {
        navigate('/files')
      }
    } catch {
      setError('Invalid username or password')
    }
  }

  async function handleTotp(e) {
    e.preventDefault()
    setError('')
    try {
      await api.post('/totp/verify', { code })
      navigate('/files')
    } catch {
      setError('Invalid TOTP code')
    }
  }

  return (
    <div style={styles.container}>
      <h2>ZTiD File Share — Login</h2>

      {!totpRequired ? (
        <form onSubmit={handleLogin} style={styles.form}>
          <label>Username</label>
          <input value={username} onChange={e => setUsername(e.target.value)} required />
          <label>Password</label>
          <input type="password" value={password} onChange={e => setPassword(e.target.value)} required />
          <button type="submit">Log in</button>
          <p style={styles.divider}>or</p>
          <a href={GITHUB_LOGIN_URL} style={styles.github}>Log in with GitHub</a>
          {error && <p style={styles.error}>{error}</p>}
          <p><Link to="/register">No account? Register</Link></p>
        </form>
      ) : (
        <form onSubmit={handleTotp} style={styles.form}>
          <p>Enter the 6-digit code from your authenticator app:</p>
          <input
            value={code}
            onChange={e => setCode(e.target.value)}
            maxLength={6}
            inputMode="numeric"
            pattern="[0-9]{6}"
            placeholder="000000"
            required
            autoFocus
          />
          <button type="submit">Verify</button>
          {error && <p style={styles.error}>{error}</p>}
        </form>
      )}
    </div>
  )
}

const styles = {
  container: { maxWidth: 360, margin: '80px auto', fontFamily: 'sans-serif' },
  form: { display: 'flex', flexDirection: 'column', gap: 8 },
  error: { color: 'red' },
  divider: { textAlign: 'center', margin: '4px 0', color: '#888' },
  github: {
    display: 'block', textAlign: 'center', padding: '8px',
    background: '#24292f', color: '#fff', borderRadius: 4, textDecoration: 'none',
  },
}
