import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/axios'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    try {
      await api.post('/auth/register', { username, password })
      navigate('/login')
    } catch (err) {
      setError(err.response?.data || 'Registration failed')
    }
  }

  return (
    <div style={styles.container}>
      <h2>ZTiD File Share — Register</h2>
      <form onSubmit={handleSubmit} style={styles.form}>
        <label>Username (min. 3 chars)</label>
        <input value={username} onChange={e => setUsername(e.target.value)} minLength={3} required />
        <label>Password (min. 8 chars)</label>
        <input type="password" value={password} onChange={e => setPassword(e.target.value)} minLength={8} required />
        <button type="submit">Register</button>
        {error && <p style={styles.error}>{error}</p>}
        <p><Link to="/login">Already have an account? Log in</Link></p>
      </form>
    </div>
  )
}

const styles = {
  container: { maxWidth: 360, margin: '80px auto', fontFamily: 'sans-serif' },
  form: { display: 'flex', flexDirection: 'column', gap: 8 },
  error: { color: 'red' },
}
