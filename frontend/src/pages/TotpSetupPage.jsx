import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import api from '../api/axios'

/**
 * TOTP setup page.
 * 1. On mount: GET /api/totp/setup → receives { secret, otpauthUri }
 * 2. Renders QR code (otpauthUri) + plain secret for manual entry
 * 3. User scans QR in authenticator, enters first code → POST /api/totp/enable
 */
export default function TotpSetupPage() {
  const navigate = useNavigate()
  const [data, setData] = useState(null)       // { secret, otpauthUri }
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [totpStatus, setTotpStatus] = useState(null) // { totpEnabled }

  useEffect(() => {
    // Check current TOTP status first
    api.get('/totp/status').then(r => setTotpStatus(r.data))
  }, [])

  async function handleSetup() {
    setError('')
    try {
      const res = await api.get('/totp/setup')
      setData(res.data)
    } catch {
      setError('Failed to generate TOTP secret')
    }
  }

  async function handleEnable(e) {
    e.preventDefault()
    setError('')
    try {
      await api.post('/totp/enable', { code })
      setSuccess(true)
      setTotpStatus({ totpEnabled: true })
    } catch {
      setError('Invalid code — try again')
    }
  }

  async function handleDisable(e) {
    e.preventDefault()
    setError('')
    try {
      await api.post('/totp/disable', { code })
      setTotpStatus({ totpEnabled: false })
      setData(null)
      setCode('')
    } catch {
      setError('Invalid code or TOTP not enabled')
    }
  }

  return (
    <div style={styles.container}>
      <h2>Two-Factor Authentication (TOTP)</h2>
      <button onClick={() => navigate('/files')} style={styles.back}>← Back to files</button>

      {totpStatus && (
        <p>Status: <strong>{totpStatus.totpEnabled ? '✅ Enabled' : '❌ Disabled'}</strong></p>
      )}

      {/* ── Enable flow ──────────────────────────────────────────────── */}
      {!totpStatus?.totpEnabled && (
        <>
          {!data ? (
            <button onClick={handleSetup}>Set up TOTP</button>
          ) : (
            <>
              <p>Scan this QR code with Google Authenticator / Authy:</p>
              <QRCodeSVG value={data.otpauthUri} size={200} />
              <p style={styles.secret}>Manual key: <code>{data.secret}</code></p>
              <form onSubmit={handleEnable} style={styles.form}>
                <label>Enter the first 6-digit code to confirm:</label>
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
                <button type="submit">Enable TOTP</button>
              </form>
              {success && <p style={styles.ok}>✅ TOTP enabled successfully!</p>}
            </>
          )}
        </>
      )}

      {/* ── Disable flow ──────────────────────────────────────────────── */}
      {totpStatus?.totpEnabled && !success && (
        <form onSubmit={handleDisable} style={styles.form}>
          <p>To disable TOTP, enter a valid code from your authenticator:</p>
          <input
            value={code}
            onChange={e => setCode(e.target.value)}
            maxLength={6}
            inputMode="numeric"
            pattern="[0-9]{6}"
            placeholder="000000"
            required
          />
          <button type="submit" style={styles.danger}>Disable TOTP</button>
        </form>
      )}

      {error && <p style={styles.error}>{error}</p>}
    </div>
  )
}

const styles = {
  container: { maxWidth: 420, margin: '60px auto', fontFamily: 'sans-serif' },
  form: { display: 'flex', flexDirection: 'column', gap: 8, marginTop: 12 },
  secret: { wordBreak: 'break-all', background: '#f4f4f4', padding: '6px 10px', borderRadius: 4 },
  error: { color: 'red' },
  ok: { color: 'green' },
  back: { marginBottom: 16 },
  danger: { background: '#c0392b', color: '#fff', border: 'none', padding: '8px', cursor: 'pointer' },
}
