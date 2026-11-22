import { useEffect, useState, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  ArrowLeft, User, Lock, Database, Search, Shield, FileText,
  Trash2, Edit3, Check, X, AlertCircle, CheckCircle2, Loader2
} from 'lucide-react'
import api from '../api/axios'

// Backend zwraca zwykle tekst; gdyby przyszedł obiekt (np. domyślny błąd Springa),
// renderowanie go w JSX wywaliłoby całą stronę.
function errorText(err, fallback) {
  const data = err.response?.data
  if (typeof data === 'string' && data.trim()) return data
  if (data && typeof data === 'object' && data.message) return data.message
  return fallback
}

export default function AccountPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [me, setMe] = useState(null)
  // Dla admina na start zawsze "account" (chyba że kliknął wprost przycisk "Zarządzanie bazą")
  const [mainTab, setMainTab] = useState(searchParams.get('tab') === 'database' ? 'database' : 'account')

  // Stan formularza własnego konta
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [msg, setMsg] = useState({ type: '', text: '' })

  // Stan wyszukiwarki Admina
  const [query, setQuery] = useState('')
  const [searchState, setSearchState] = useState({
    loading: false,
    tooMany: false,
    count: 0,
    users: [],
  })
  const searchTimer = useRef(null)

  // Wybrany użytkownik do edycji przez Admina
  const [selectedUser, setSelectedUser] = useState(null)
  const [editSubTab, setEditSubTab] = useState('profile') // 'profile' | 'files'
  const [editForm, setEditForm] = useState({ username: '', password: '', role: 'ROLE_USER', disableTotp: false })
  const [userFiles, setUserFiles] = useState([])
  const [renamingFileId, setRenamingFileId] = useState(null)
  const [renameValue, setRenameValue] = useState('')

  const isAdmin = me?.role === 'ROLE_ADMIN'

  useEffect(() => {
    api.get('/auth/me')
      .then(res => {
        setMe(res.data)
        setUsername(res.data.username)
        // Zwykły user nie może wejść w zakładkę database
        if (res.data.role !== 'ROLE_ADMIN') {
          setMainTab('account')
        }
      })
      .catch(() => navigate('/login'))
  }, [navigate])

  async function handleUpdateOwnAccount(e) {
    e.preventDefault()
    setMsg({ type: '', text: '' })
    try {
      const res = await api.put('/auth/me', { username, password })
      if (res.data.token) {
        localStorage.setItem('token', res.data.token)
      }
      setMe(res.data.user)
      setPassword('')
      setMsg({ type: 'ok', text: 'Dane konta zostały zaktualizowane.' })
    } catch (err) {
      setMsg({ type: 'err', text: errorText(err, 'Nie udało się zapisać zmian.') })
    }
  }

  function handleSearchChange(val) {
    setQuery(val)
    setSelectedUser(null)
    runSearch(val)
  }

  function runSearch(val) {
    clearTimeout(searchTimer.current)

    if (!val.trim()) {
      setSearchState({ loading: false, tooMany: false, count: 0, users: [] })
      return
    }

    setSearchState(prev => ({ ...prev, loading: true }))
    searchTimer.current = setTimeout(async () => {
      try {
        const res = await api.get('/auth/admin/search', { params: { q: val.trim() } })
        setSearchState({
          loading: false,
          tooMany: res.data.tooMany,
          count: res.data.count,
          users: res.data.users,
        })
      } catch {
        setSearchState({ loading: false, tooMany: false, count: 0, users: [] })
      }
    }, 300)
  }

  async function selectUserForEdit(user) {
    setSelectedUser(user)
    setEditSubTab('profile')
    setMsg({ type: '', text: '' })
    setEditForm({
      username: user.username,
      password: '',
      role: user.role,
      disableTotp: false,
    })
    try {
      const res = await api.get(`/files/admin/user/${user.username}`)
      setUserFiles(res.data)
    } catch {
      setUserFiles([])
    }
  }

  async function handleAdminSaveUser(e) {
    e.preventDefault()
    if (!selectedUser) return
    setMsg({ type: '', text: '' })
    try {
      const res = await api.put(`/auth/admin/users/${selectedUser.id}`, editForm)
      setSelectedUser(res.data)
      setEditForm(prev => ({ ...prev, password: '', disableTotp: false }))
      setMsg({ type: 'ok', text: `Zaktualizowano użytkownika ${res.data.username}.` })
      // Odśwież listę wyników wyszukiwania (bez zamykania okna edycji)
      runSearch(query)
      const filesRes = await api.get(`/files/admin/user/${res.data.username}`)
      setUserFiles(filesRes.data)
    } catch (err) {
      setMsg({ type: 'err', text: errorText(err, 'Błąd aktualizacji użytkownika.') })
    }
  }

  async function handleAdminRenameFile(fileId) {
    if (!renameValue.trim() || !selectedUser) return
    try {
      await api.patch(`/files/${fileId}/rename`, { originalName: renameValue.trim() })
      setRenamingFileId(null)
      const res = await api.get(`/files/admin/user/${selectedUser.username}`)
      setUserFiles(res.data)
    } catch {
      setMsg({ type: 'err', text: 'Nie udało się zmienić nazwy pliku.' })
    }
  }

  async function handleAdminDeleteFile(fileId) {
    if (!window.confirm('Usunąć ten plik z bazy?') || !selectedUser) return
    try {
      await api.delete(`/files/${fileId}`)
      const res = await api.get(`/files/admin/user/${selectedUser.username}`)
      setUserFiles(res.data)
    } catch {
      setMsg({ type: 'err', text: 'Nie udało się usunąć pliku.' })
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 py-10 px-4">
      <div className="max-w-3xl mx-auto">
        <button
          onClick={() => navigate('/files')}
          className="text-sm text-slate-400 hover:text-white flex items-center gap-1.5 mb-6 transition cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Wróć do plików</span>
        </button>

        {/* Główne zakładki (dla Admina: Moje konto | Zarządzanie bazą) */}
        {isAdmin && (
          <div className="grid grid-cols-2 gap-1 p-1 bg-slate-900 rounded-xl border border-slate-800 mb-6">
            <button
              type="button"
              onClick={() => { setMainTab('account'); setMsg({ type: '', text: '' }) }}
              className={`py-2.5 text-sm font-medium rounded-lg flex items-center justify-center gap-2 transition cursor-pointer ${
                mainTab === 'account'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <User className="w-4 h-4" />
              <span>Ustawienia mojego konta</span>
            </button>
            <button
              type="button"
              onClick={() => { setMainTab('database'); setMsg({ type: '', text: '' }) }}
              className={`py-2.5 text-sm font-medium rounded-lg flex items-center justify-center gap-2 transition cursor-pointer ${
                mainTab === 'database'
                  ? 'bg-amber-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Database className="w-4 h-4" />
              <span>Zarządzanie bazą (Admin)</span>
            </button>
          </div>
        )}

        {msg.text && (
          <div
            className={`mb-6 p-3.5 rounded-xl border text-sm flex items-center gap-2 ${
              msg.type === 'ok'
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                : 'bg-rose-500/10 border-rose-500/30 text-rose-300'
            }`}
          >
            {msg.type === 'ok' ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertCircle className="w-4 h-4 shrink-0" />}
            <span>{msg.text}</span>
          </div>
        )}

        {/* WIDOK 1: Ustawienia własnego konta */}
        {mainTab === 'account' && (
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-xl">
            <h2 className="text-lg font-semibold text-white mb-1">Ustawienia konta</h2>
            <p className="text-sm text-slate-400 mb-6">
              Zmień swoją nazwę użytkownika, zaktualizuj hasło lub skonfiguruj uwierzytelnianie dwuskładnikowe.
            </p>

            <form onSubmit={handleUpdateOwnAccount} className="space-y-4 max-w-md">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  Nazwa użytkownika
                </label>
                <div className="relative">
                  <User className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
                  <input
                    value={username}
                    onChange={e => setUsername(e.target.value)}
                    minLength={3}
                    required
                    className="w-full pl-10 pr-3 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-sm text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">
                  Nowe hasło (zostaw puste, aby nie zmieniać)
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
                  <input
                    type="password"
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    minLength={8}
                    placeholder="Min. 8 znaków"
                    className="w-full pl-10 pr-3 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500"
                  />
                </div>
              </div>

              <div className="pt-2 flex items-center gap-3">
                <button
                  type="submit"
                  className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-xl transition cursor-pointer"
                >
                  Zapisz zmiany
                </button>
                <button
                  type="button"
                  onClick={() => navigate('/totp-setup')}
                  className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-sm font-medium rounded-xl flex items-center gap-2 transition cursor-pointer"
                >
                  <Shield className="w-4 h-4 text-emerald-400" />
                  <span>Ustawienia 2FA</span>
                </button>
              </div>
            </form>
          </div>
        )}

        {/* WIDOK 2: Zarządzanie bazą (tylko Admin) */}
        {mainTab === 'database' && isAdmin && (
          <div className="space-y-6">
            {/* Wyszukiwarka użytkowników */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
              <h2 className="text-lg font-semibold text-white mb-1">Wyszukaj użytkownika w bazie</h2>
              <p className="text-xs text-slate-400 mb-4">
                Wyświetla maksymalnie 10 dopasowań. Jeśli pasuje więcej rekordów, doprecyzuj zapytanie.
              </p>

              <div className="relative">
                <Search className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
                <input
                  value={query}
                  onChange={e => handleSearchChange(e.target.value)}
                  placeholder="Wpisz fragment nazwy użytkownika..."
                  className="w-full pl-10 pr-10 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm text-white placeholder-slate-500 focus:outline-none focus:border-amber-500"
                />
                {searchState.loading && (
                  <Loader2 className="w-4 h-4 text-amber-400 animate-spin absolute right-3.5 top-3" />
                )}
              </div>

              {/* Komunikat o przekroczeniu 10 wyników */}
              {searchState.tooMany && (
                <div className="mt-4 p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-sm">
                  Znaleziono <strong>{searchState.count}</strong> dopasowań — dane nie zostały pobrane z bazy. Wpisz więcej znaków, aby zawęzić wynik do max. 10 użytkowników.
                </div>
              )}

              {/* Lista max. 10 dopasowań */}
              {!searchState.tooMany && searchState.users.length > 0 && (
                <div className="mt-4 divide-y divide-slate-800 border border-slate-800 rounded-xl overflow-hidden">
                  {searchState.users.map(u => (
                    <button
                      key={u.id}
                      type="button"
                      onClick={() => selectUserForEdit(u)}
                      className={`w-full px-4 py-3 text-left flex items-center justify-between hover:bg-slate-800/70 transition cursor-pointer ${
                        selectedUser?.id === u.id ? 'bg-slate-800' : 'bg-slate-950/60'
                      }`}
                    >
                      <div>
                        <span className="font-medium text-white">{u.username}</span>
                        <span className="ml-2 text-xs text-slate-500">ID #{u.id}</span>
                      </div>
                      <span
                        className={`px-2 py-0.5 text-xs rounded border ${
                          u.role === 'ROLE_ADMIN'
                            ? 'bg-amber-500/10 text-amber-300 border-amber-500/30'
                            : 'bg-slate-800 text-slate-300 border-slate-700'
                        }`}
                      >
                        {u.role}
                      </span>
                    </button>
                  ))}
                </div>
              )}

              {!searchState.loading && !searchState.tooMany && query.trim() && searchState.users.length === 0 && (
                <p className="mt-4 text-sm text-slate-500">Brak użytkowników pasujących do zapytania.</p>
              )}
            </div>

            {/* Okno edycji wybranego użytkownika z podziałkami */}
            {selectedUser && (
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h3 className="text-base font-semibold text-white">
                      Zarządzanie: <span className="text-amber-400">{selectedUser.username}</span>
                    </h3>
                    <p className="text-xs text-slate-400">ID #{selectedUser.id}</p>
                  </div>
                  <button
                    onClick={() => setSelectedUser(null)}
                    className="p-1.5 text-slate-400 hover:text-white rounded-lg cursor-pointer"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>

                {/* Podziałki wewnątrz okna edycji */}
                <div className="grid grid-cols-2 gap-1 p-1 bg-slate-950 rounded-xl border border-slate-800 mb-6">
                  <button
                    type="button"
                    onClick={() => setEditSubTab('profile')}
                    className={`py-2 text-xs font-medium rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer ${
                      editSubTab === 'profile'
                        ? 'bg-amber-600 text-white'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <User className="w-3.5 h-3.5" />
                    <span>Edycja użytkownika</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditSubTab('files')}
                    className={`py-2 text-xs font-medium rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer ${
                      editSubTab === 'files'
                        ? 'bg-amber-600 text-white'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <FileText className="w-3.5 h-3.5" />
                    <span>Pliki użytkownika ({userFiles.length})</span>
                  </button>
                </div>

                {/* Podziałka A: Edycja danych użytkownika */}
                {editSubTab === 'profile' && (
                  <form onSubmit={handleAdminSaveUser} className="space-y-4 max-w-md">
                    <div>
                      <label className="block text-xs text-slate-400 mb-1">Nazwa użytkownika</label>
                      <input
                        value={editForm.username}
                        onChange={e => setEditForm(prev => ({ ...prev, username: e.target.value }))}
                        required
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-white focus:outline-none focus:border-amber-500"
                      />
                    </div>

                    <div>
                      <label className="block text-xs text-slate-400 mb-1">
                        Reset hasła (zostaw puste, aby nie zmieniać)
                      </label>
                      <input
                        type="password"
                        value={editForm.password}
                        onChange={e => setEditForm(prev => ({ ...prev, password: e.target.value }))}
                        placeholder="Nowe hasło..."
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-white focus:outline-none focus:border-amber-500"
                      />
                    </div>

                    <div>
                      <label className="block text-xs text-slate-400 mb-1">Rola systemowa</label>
                      <select
                        value={editForm.role}
                        onChange={e => setEditForm(prev => ({ ...prev, role: e.target.value }))}
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-white focus:outline-none focus:border-amber-500"
                      >
                        <option value="ROLE_USER">ROLE_USER</option>
                        <option value="ROLE_ADMIN">ROLE_ADMIN</option>
                      </select>
                    </div>

                    {selectedUser.totpEnabled && (
                      <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer pt-1">
                        <input
                          type="checkbox"
                          checked={editForm.disableTotp}
                          onChange={e => setEditForm(prev => ({ ...prev, disableTotp: e.target.checked }))}
                          className="rounded border-slate-700 bg-slate-950"
                        />
                        <span>Wymuś wyłączenie 2FA (TOTP) dla tego użytkownika</span>
                      </label>
                    )}

                    <button
                      type="submit"
                      className="px-5 py-2 bg-amber-600 hover:bg-amber-500 text-white text-sm font-medium rounded-xl transition cursor-pointer"
                    >
                      Zapisz dane użytkownika
                    </button>
                  </form>
                )}

                {/* Podziałka B: Pliki użytkownika */}
                {editSubTab === 'files' && (
                  <div>
                    {userFiles.length === 0 ? (
                      <p className="text-sm text-slate-500 py-4">Ten użytkownik nie posiada jeszcze żadnych plików.</p>
                    ) : (
                      <div className="divide-y divide-slate-800 border border-slate-800 rounded-xl overflow-hidden">
                        {userFiles.map(f => (
                          <div key={f.id} className="p-3.5 bg-slate-950/60 flex items-center justify-between gap-3">
                            <div className="min-w-0 flex-1">
                              {renamingFileId === f.id ? (
                                <div className="flex items-center gap-1.5">
                                  <input
                                    value={renameValue}
                                    onChange={e => setRenameValue(e.target.value)}
                                    className="px-2.5 py-1 bg-slate-900 border border-amber-500 rounded-lg text-xs text-white w-full"
                                    autoFocus
                                  />
                                  <button
                                    onClick={() => handleAdminRenameFile(f.id)}
                                    className="p-1.5 bg-emerald-600 text-white rounded-lg cursor-pointer"
                                  >
                                    <Check className="w-3.5 h-3.5" />
                                  </button>
                                  <button
                                    onClick={() => setRenamingFileId(null)}
                                    className="p-1.5 bg-slate-800 text-slate-300 rounded-lg cursor-pointer"
                                  >
                                    <X className="w-3.5 h-3.5" />
                                  </button>
                                </div>
                              ) : (
                                <>
                                  <p className="text-sm font-medium text-white truncate">{f.originalName}</p>
                                  <p className="text-xs text-slate-500">
                                    Właściciel: {f.owner} • Udostępniono: {f.sharedWith || 'nikomu'}
                                  </p>
                                </>
                              )}
                            </div>

                            {renamingFileId !== f.id && (
                              <div className="flex items-center gap-1.5 shrink-0">
                                <button
                                  onClick={() => {
                                    setRenamingFileId(f.id)
                                    setRenameValue(f.originalName)
                                  }}
                                  title="Zmień nazwę pliku"
                                  className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg transition cursor-pointer"
                                >
                                  <Edit3 className="w-3.5 h-3.5" />
                                </button>
                                <button
                                  onClick={() => handleAdminDeleteFile(f.id)}
                                  title="Usuń plik"
                                  className="p-2 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/20 rounded-lg transition cursor-pointer"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}