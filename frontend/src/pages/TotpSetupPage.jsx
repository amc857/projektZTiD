import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { ArrowLeft, Shield, CheckCircle2, AlertCircle } from 'lucide-react'
import api from '../api/axios'

export default function TotpSetupPage() {
  const navigate = useNavigate()
  const [data, setData] = useState(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [totpStatus, setTotpStatus] = useState(null)

  useEffect(() => {
    api.get('/totp/status').then(r => setTotpStatus(r.data))
  }, [])

  async function handleSetup() {
    setError('')
    try {
      const res = await api.get('/totp/setup')
      setData(res.data)
    } catch {
      setError('Nie udało się wygenerować sekretu TOTP')
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
      setError('Nieprawidłowy kod — spróbuj ponownie')
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
      setSuccess(false)
    } catch {
      setError('Nieprawidłowy kod lub 2FA nie jest włączone')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-4 bg-slate-950 text-slate-100">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl p-8">
        <button
          onClick={() => navigate('/files')}
          className="text-sm text-slate-400 hover:text-white flex items-center gap-1.5 mb-6 transition cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Wróć do plików</span>
        </button>

        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center">
            <Shield className="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-white">Uwierzytelnianie dwuskładnikowe</h2>
            {totpStatus && (
              <p className="text-xs text-slate-400">
                Status:{' '}
                <span className={totpStatus.totpEnabled ? 'text-emerald-400 font-medium' : 'text-slate-400'}>
                  {totpStatus.totpEnabled ? 'Aktywne' : 'Wyłączone'}
                </span>
              </p>
            )}
          </div>
        </div>

        {error && (
          <div className="my-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {!totpStatus?.totpEnabled && (
          <div className="mt-6">
            {!data ? (
              <button
                onClick={handleSetup}
                className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-xl transition cursor-pointer"
              >
                Skonfiguruj 2FA (TOTP)
              </button>
            ) : (
              <div className="space-y-4">
                <p className="text-sm text-slate-300">
                  Zeskanuj kod QR w aplikacji Google Authenticator lub Authy:
                </p>
                <div className="flex justify-center">
                  <div className="bg-white p-3 rounded-xl">
                    <QRCodeSVG value={data.otpauthUri} size={180} />
                  </div>
                </div>
                <div className="p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-400 break-all">
                  Klucz ręczny: <code className="text-indigo-300">{data.secret}</code>
                </div>
                <form onSubmit={handleEnable} className="space-y-3">
                  <label className="block text-xs text-slate-400">
                    Wpisz pierwszy 6-cyfrowy kod w celu potwierdzenia:
                  </label>
                  <input
                    value={code}
                    onChange={e => setCode(e.target.value)}
                    maxLength={6}
                    inputMode="numeric"
                    pattern="[0-9]{6}"
                    placeholder="000000"
                    required
                    autoFocus
                    className="w-full text-center tracking-[0.5em] text-lg font-mono py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-white focus:outline-none focus:border-indigo-500"
                  />
                  <button
                    type="submit"
                    className="w-full py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium rounded-xl transition cursor-pointer"
                  >
                    Włącz 2FA
                  </button>
                </form>
              </div>
            )}
          </div>
        )}

        {success && (
          <div className="mt-4 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-sm flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>Uwierzytelnianie TOTP zostało włączone!</span>
          </div>
        )}

        {totpStatus?.totpEnabled && !success && (
          <form onSubmit={handleDisable} className="mt-6 space-y-3">
            <p className="text-sm text-slate-300">
              Aby wyłączyć 2FA, podaj aktualny 6-cyfrowy kod z aplikacji:
            </p>
            <input
              value={code}
              onChange={e => setCode(e.target.value)}
              maxLength={6}
              inputMode="numeric"
              pattern="[0-9]{6}"
              placeholder="000000"
              required
              className="w-full text-center tracking-[0.5em] text-lg font-mono py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-white focus:outline-none focus:border-rose-500"
            />
            <button
              type="submit"
              className="w-full py-2.5 bg-rose-600 hover:bg-rose-500 text-white text-sm font-medium rounded-xl transition cursor-pointer"
            >
              Wyłącz 2FA
            </button>
          </form>
        )}
      </div>
    </div>
  )
}