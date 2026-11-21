import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Upload, Download, Share2, Trash2, Shield, ShieldCheck,
  LogOut, FileText, Users, Check, X, Loader2, 
  User, Database
} from 'lucide-react'
import api from '../api/axios'

export default function FilesPage() {
  const navigate = useNavigate()
  const [me, setMe] = useState(null) // { id, username, role, totpEnabled }
  const [activeTab, setActiveTab] = useState('files') // 'files' | 'admin'
  const [files, setFiles] = useState([])
  const [users, setUsers] = useState([])
  const [error, setError] = useState('')

  // Stan dla Share z walidacją na żywo:
  const [shareTarget, setShareTarget] = useState({})   // { [fileId]: username }
  const [userExists, setUserExists] = useState({})     // { [fileId]: true | false | 'loading' | null }
  const debounceTimers = useRef({})
  const fileInputRef = useRef()

  const isAdmin = me?.role === 'ROLE_ADMIN'

  useEffect(() => {
    initPage()
  }, [])

  async function initPage() {
    try {
      const meRes = await api.get('/auth/me')
      setMe(meRes.data)
      await fetchFiles()
      if (meRes.data.role === 'ROLE_ADMIN') {
        await fetchUsers()
      }
    } catch {
      navigate('/login')
    }
  }

  async function fetchFiles() {
    try {
      const res = await api.get('/files')
      setFiles(res.data)
    } catch {
      setError('Nie udało się pobrać listy plików')
    }
  }

  async function fetchUsers() {
    try {
      const res = await api.get('/auth/users')
      setUsers(res.data)
    } catch {
      setError('Nie udało się pobrać listy użytkowników')
    }
  }

  async function handleUpload(e) {
    const file = e.target.files[0]
    if (!file) return
    setError('')
    const form = new FormData()
    form.append('file', file)
    try {
      await api.post('/files/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      e.target.value = ''
      await fetchFiles()
    } catch {
      setError('Wgrywanie pliku nie powiodło się')
    }
  }

  async function handleDownload(id, name) {
    try {
      const res = await api.get(`/files/${id}/download`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', name)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      setError('Nie udało się pobrać pliku')
    }
  }

  function handleShareInputChange(fileId, value) {
    setShareTarget(prev => ({ ...prev, [fileId]: value }))
    clearTimeout(debounceTimers.current[fileId])

    const trimmed = value.trim()
    if (!trimmed || trimmed === me?.username) {
      setUserExists(prev => ({ ...prev, [fileId]: null }))
      return
    }

    setUserExists(prev => ({ ...prev, [fileId]: 'loading' }))
    debounceTimers.current[fileId] = setTimeout(async () => {
      try {
        const res = await api.get('/auth/exists', { params: { username: trimmed } })
        setUserExists(prev => ({ ...prev, [fileId]: res.data.exists }))
      } catch {
        setUserExists(prev => ({ ...prev, [fileId]: false }))
      }
    }, 300)
  }

  async function handleShare(id) {
    const target = shareTarget[id]?.trim()
    if (!target || userExists[id] !== true) return
    setError('')
    try {
      await api.post(`/files/${id}/share`, { username: target })
      setShareTarget(prev => ({ ...prev, [id]: '' }))
      setUserExists(prev => ({ ...prev, [id]: null }))
      await fetchFiles()
    } catch {
      setError('Udostępnienie nie powiodło się')
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('Czy na pewno chcesz usunąć ten plik?')) return
    setError('')
    try {
      await api.delete(`/files/${id}`)
      await fetchFiles()
    } catch {
      setError('Brak uprawnień lub błąd podczas usuwania pliku')
    }
  }

  async function handleRoleChange(userId, newRole) {
    setError('')
    try {
      await api.patch(`/auth/users/${userId}/role`, { role: newRole })
      await fetchUsers()
    } catch {
      setError('Nie udało się zmienić roli użytkownika')
    }
  }

  async function handleLogout() {
    try {
      await api.post('/auth/logout')
    } catch {}
    localStorage.removeItem('token')
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      {/* Górny pasek nawigacji */}
      <header className="border-b border-slate-800 bg-slate-900/70 backdrop-blur sticky top-0 z-10">
        <div className="flex items-center gap-2">
          {me && (
            <button
              onClick={() => navigate('/account')}
              title="Ustawienia konta"
              className="px-3 py-1.5 text-sm bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-lg flex items-center gap-2 transition cursor-pointer"
            >
              <User className="w-4 h-4 text-indigo-400" />
              <span className="font-medium text-white">{me.username}</span>
              {isAdmin && (
                <span className="px-1.5 py-0.5 text-[10px] font-semibold bg-amber-500/15 text-amber-300 border border-amber-500/30 rounded">
                  ADMIN
                </span>
              )}
            </button>
          )}

          {isAdmin && (
            <button
              onClick={() => navigate('/account?tab=database')}
              className="px-3 py-1.5 text-sm bg-amber-600/20 hover:bg-amber-600/30 text-amber-300 border border-amber-500/30 rounded-lg flex items-center gap-1.5 transition cursor-pointer"
            >
              <Database className="w-4 h-4" />
              <span>Zarządzanie bazą</span>
            </button>
          )}

          <button
            onClick={() => navigate('/totp-setup')}
            className="px-3 py-1.5 text-sm bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-lg flex items-center gap-1.5 transition cursor-pointer"
          >
            <Shield className="w-4 h-4 text-emerald-400" />
            <span>2FA</span>
          </button>

          <button
            onClick={handleLogout}
            className="px-3 py-1.5 text-sm bg-slate-800 hover:bg-rose-950/60 hover:text-rose-300 border border-slate-700 rounded-lg flex items-center gap-1.5 transition cursor-pointer"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 py-8">
        {error && (
          <div className="mb-6 p-3.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm">
            {error}
          </div>
        )}

        {/* Zakładki dla Administratora + Przycisk Upload */}
        <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
          <div className="flex items-center gap-2">
            <button
              onClick={() => setActiveTab('files')}
              className={`px-4 py-2 text-sm font-medium rounded-xl flex items-center gap-2 transition cursor-pointer ${
                activeTab === 'files'
                  ? 'bg-indigo-600 text-white'
                  : 'bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-800'
              }`}
            >
              <FileText className="w-4 h-4" />
              <span>{isAdmin ? 'Wszystkie pliki (Admin)' : 'Moje i udostępnione pliki'}</span>
            </button>

            {isAdmin && (
              <button
                onClick={() => setActiveTab('admin')}
                className={`px-4 py-2 text-sm font-medium rounded-xl flex items-center gap-2 transition cursor-pointer ${
                  activeTab === 'admin'
                    ? 'bg-amber-600 text-white'
                    : 'bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-800'
                }`}
              >
                <Users className="w-4 h-4" />
                <span>Użytkownicy i role (RBAC)</span>
              </button>
            )}
          </div>

          {activeTab === 'files' && (
            <div>
              <button
                onClick={() => fileInputRef.current.click()}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-xl flex items-center gap-2 shadow-lg shadow-indigo-600/20 transition cursor-pointer"
              >
                <Upload className="w-4 h-4" />
                <span>Wgraj plik</span>
              </button>
              <input ref={fileInputRef} type="file" hidden onChange={handleUpload} />
            </div>
          )}
        </div>

        {/* WIDOK 1: Tabela plików */}
        {activeTab === 'files' && (
          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
            {files.length === 0 ? (
              <div className="p-12 text-center text-slate-500">
                Brak plików do wyświetlenia. Kliknij „Wgraj plik”, aby dodać pierwszy dokument.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-slate-800 bg-slate-950/50 text-xs uppercase text-slate-400">
                      <th className="py-3.5 px-4">Nazwa pliku</th>
                      <th className="py-3.5 px-4">Właściciel</th>
                      <th className="py-3.5 px-4">Data dodania</th>
                      <th className="py-3.5 px-4">Udostępniono dla</th>
                      <th className="py-3.5 px-4 text-right">Akcje</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/70 text-sm">
                    {files.map(f => {
                      const canManage = isAdmin || f.owner === me?.username
                      const status = userExists[f.id]

                      return (
                        <tr key={f.id} className="hover:bg-slate-800/40 transition">
                          <td className="py-3.5 px-4 font-medium text-white">{f.originalName}</td>
                          <td className="py-3.5 px-4 text-slate-300">{f.owner}</td>
                          <td className="py-3.5 px-4 text-slate-400">
                            {new Date(f.uploadedAt).toLocaleString()}
                          </td>
                          <td className="py-3.5 px-4 text-slate-400">
                            {f.sharedWith || <span className="text-slate-600">—</span>}
                          </td>
                          <td className="py-3.5 px-4">
                            <div className="flex items-center justify-end gap-2">
                              <button
                                onClick={() => handleDownload(f.id, f.originalName)}
                                title="Pobierz"
                                className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg transition cursor-pointer"
                              >
                                <Download className="w-4 h-4" />
                              </button>

                              {/* Share tylko dla właściciela */}
                              {f.owner === me?.username && (
                                <div className="flex items-center gap-1">
                                  <div className="relative">
                                    <input
                                      placeholder="login..."
                                      value={shareTarget[f.id] || ''}
                                      onChange={e => handleShareInputChange(f.id, e.target.value)}
                                      className="w-32 pl-2.5 pr-7 py-1.5 bg-slate-950 border border-slate-700 rounded-lg text-xs text-white focus:outline-none focus:border-indigo-500"
                                    />
                                    <span className="absolute right-2 top-1.5">
                                      {status === 'loading' && (
                                        <Loader2 className="w-3.5 h-3.5 text-slate-400 animate-spin" />
                                      )}
                                      {status === true && (
                                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                                      )}
                                      {status === false && (
                                        <X className="w-3.5 h-3.5 text-rose-400" />
                                      )}
                                    </span>
                                  </div>
                                  <button
                                    onClick={() => handleShare(f.id)}
                                    disabled={status !== true}
                                    title="Udostępnij"
                                    className="px-2.5 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-800 disabled:text-slate-600 text-white text-xs font-medium rounded-lg flex items-center gap-1 transition cursor-pointer disabled:cursor-not-allowed"
                                  >
                                    <Share2 className="w-3.5 h-3.5" />
                                    <span>Share</span>
                                  </button>
                                </div>
                              )}

                              {/* Usuwanie dla właściciela lub ADMINA */}
                              {canManage && (
                                <button
                                  onClick={() => handleDelete(f.id)}
                                  title="Usuń plik"
                                  className="p-2 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/20 rounded-lg transition cursor-pointer"
                                >
                                  <Trash2 className="w-4 h-4" />
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* WIDOK 2: Panel Admina RBAC */}
        {activeTab === 'admin' && isAdmin && (
          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-800 bg-slate-950/50 text-xs uppercase text-slate-400">
                  <th className="py-3.5 px-4">ID</th>
                  <th className="py-3.5 px-4">Użytkownik</th>
                  <th className="py-3.5 px-4">2FA (TOTP)</th>
                  <th className="py-3.5 px-4">Aktualna rola</th>
                  <th className="py-3.5 px-4 text-right">Zmień rolę</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/70 text-sm">
                {users.map(u => (
                  <tr key={u.id} className="hover:bg-slate-800/40 transition">
                    <td className="py-3.5 px-4 text-slate-500">#{u.id}</td>
                    <td className="py-3.5 px-4 font-medium text-white">{u.username}</td>
                    <td className="py-3.5 px-4">
                      {u.totpEnabled ? (
                        <span className="text-emerald-400 text-xs">Aktywne</span>
                      ) : (
                        <span className="text-slate-500 text-xs">Wyłączone</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`px-2 py-0.5 text-xs rounded-md border ${
                          u.role === 'ROLE_ADMIN'
                            ? 'bg-amber-500/10 text-amber-300 border-amber-500/30'
                            : 'bg-slate-800 text-slate-300 border-slate-700'
                        }`}
                      >
                        {u.role}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <select
                        value={u.role}
                        disabled={u.username === me?.username}
                        onChange={e => handleRoleChange(u.id, e.target.value)}
                        className="bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none focus:border-amber-500 disabled:opacity-40"
                      >
                        <option value="ROLE_USER">ROLE_USER</option>
                        <option value="ROLE_ADMIN">ROLE_ADMIN</option>
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  )
}