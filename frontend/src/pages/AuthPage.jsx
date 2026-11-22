import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Lock, User, ShieldCheck, CheckCircle2, AlertCircle } from 'lucide-react'
import api from '../api/axios'

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080'
const GITHUB_LOGIN_URL = `${BACKEND_URL}/oauth2/authorization/github`

export default function AuthPage({ defaultTab = 'login' }) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [tab, setTab] = useState(searchParams.get('tab') || defaultTab)

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [totpRequired, setTotpRequired] = useState(false)
  const [code, setCode] = useState('')
  const [error, setError] = useState(
    searchParams.get('error') === 'github' ? 'Logowanie przez GitHub nie powiodło się — spróbuj ponownie.' : ''
  )
  const [info, setInfo] = useState('')
  const [loading, setLoading] = useState(false)

  function switchTab(nextTab) {
    setTab(nextTab)
    setError('')
    setInfo('')
    setTotpRequired(false)
  }

  async function handleLogin(e) {
    e.preventDefault()
    setError('')
    setInfo('')
    setLoading(true)
    try {
      const res = await api.post('/auth/login', { username, password })
      if (res.data.totpRequired) {
        setTotpRequired(true)
      } else {
        if (res.data.token) localStorage.setItem('token', res.data.token)
        navigate('/files')
      }
    } catch {
      setError('Nieprawidłowa nazwa użytkownika lub hasło')
    } finally {
      setLoading(false)
    }
  }

  async function handleRegister(e) {
    e.preventDefault()
    setError('')
    setInfo('')
    setLoading(true)
    try {
      await api.post('/auth/register', { username, password })
      setPassword('')
      setTab('login')
      setInfo('Konto zostało utworzone! Możesz się teraz zalogować.')
    } catch (err) {
      const data = err.response?.data
      setError(typeof data === 'string' && data ? data : 'Rejestracja nie powiodła się')
    } finally {
      setLoading(false)
    }
  }

  async function handleTotp(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await api.post('/totp/verify', { code })
      if (res.data.token) localStorage.setItem('token', res.data.token)
      navigate('/files')
    } catch {
      setError('Nieprawidłowy kod TOTP')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-4 bg-slate-950">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl p-8">
        <div className="flex flex-col items-center mb-6">
          <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/30 flex items-center justify-center mb-3">
            <ShieldCheck className="w-6 h-6 text-indigo-400" />
          </div>
          <h1 className="text-xl font-semibold text-white">ZTiD File Share</h1>
          <p className="text-sm text-slate-400 mt-1">Bezpieczne udostępnianie plików</p>
        </div>

        {!totpRequired && (
          <div className="grid grid-cols-2 gap-1 p-1 bg-slate-950 rounded-xl border border-slate-800 mb-6">
            <button
              type="button"
              onClick={() => switchTab('login')}
              className={`py-2 text-sm font-medium rounded-lg transition cursor-pointer ${
                tab === 'login'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Logowanie
            </button>
            <button
              type="button"
              onClick={() => switchTab('register')}
              className={`py-2 text-sm font-medium rounded-lg transition cursor-pointer ${
                tab === 'register'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Rejestracja
            </button>
          </div>
        )}

        {info && (
          <div className="mb-4 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-sm flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{info}</span>
          </div>
        )}
        {error && (
          <div className="mb-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {totpRequired ? (
          <form onSubmit={handleTotp} className="space-y-4">
            <p className="text-sm text-slate-300 text-center">
              Wpisz 6-cyfrowy kod z aplikacji uwierzytelniającej:
            </p>
            <input
              value={code}
              onChange={e => setCode(e.target.value)}
              maxLength={6}
              inputMode="numeric"
              pattern="[0-9]{6}"
              placeholder="000000"
              required
              autoFocus
              className="w-full text-center tracking-[0.5em] text-xl font-mono py-3 bg-slate-950 border border-slate-700 rounded-xl text-white focus:outline-none focus:border-indigo-500"
            />
            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium rounded-xl transition cursor-pointer"
            >
              {loading ? 'Weryfikacja...' : 'Potwierdź kod 2FA'}
            </button>
            <button
              type="button"
              onClick={() => switchTab('login')}
              className="w-full text-xs text-slate-400 hover:text-slate-200 pt-1 cursor-pointer"
            >
              ← Wróć do logowania
            </button>
          </form>
        ) : (
          <form onSubmit={tab === 'login' ? handleLogin : handleRegister} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1.5">
                Nazwa użytkownika {tab === 'register' && '(min. 3 znaki)'}
              </label>
              <div className="relative">
                <User className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
                <input
                  value={username}
                  onChange={e => setUsername(e.target.value)}
                  minLength={tab === 'register' ? 3 : 1}
                  required
                  placeholder="np. adam"
                  className="w-full pl-10 pr-3 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1.5">
                Hasło {tab === 'register' && '(min. 8 znaków)'}
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
                <input
                  type="password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  minLength={tab === 'register' ? 8 : 1}
                  required
                  placeholder="••••••••"
                  className="w-full pl-10 pr-3 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium rounded-xl transition cursor-pointer"
            >
              {loading
                ? 'Przetwarzanie...'
                : tab === 'login'
                ? 'Zaloguj się'
                : 'Utwórz konto'}
            </button>

            {tab === 'login' && (
              <>
                <div className="relative flex items-center py-2">
                  <div className="grow border-t border-slate-800" />
                  <span className="shrink mx-3 text-xs text-slate-500 uppercase">lub</span>
                  <div className="grow border-t border-slate-800" />
                </div>

                <a
                  href={GITHUB_LOGIN_URL}
                  className="w-full py-2.5 px-4 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-100 text-sm font-medium rounded-xl flex items-center justify-center gap-2 transition"
                >
                  <svg className="w-4 h-4 fill-current" viewBox="0 0 24 24" aria-hidden="true">
                    <path
                    fillRule="evenodd"
                    clipRule="evenodd"
                    d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
                    />
                </svg>
                <span>Kontynuuj z GitHub</span>
                </a>
              </>
            )}
          </form>
        )}
      </div>
    </div>
  )
}