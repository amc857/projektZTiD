import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/axios'

/**
 * Login page.
 * Flow:
 *   1. Submit username + password → POST /api/auth/login
 *   2a. totpRequired=false → navigate to /files
 *   2b. totpRequired=true  → show TOTP code field → POST /api/totp/verify
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [totpRequired, setTotpRequired] = useState(false)
  const [code, setCode] = useState('')
  const [error, setError] = useState('')

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
}
