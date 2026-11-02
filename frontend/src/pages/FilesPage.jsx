import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'

/**
 * Main file management page.
 * Features: upload, list (own + shared), download, share with another user.
 */
export default function FilesPage() {
  const navigate = useNavigate()
  const [files, setFiles] = useState([])
  const [error, setError] = useState('')
  const [shareTarget, setShareTarget] = useState({}) // { [fileId]: username }
  const fileInputRef = useRef()

  useEffect(() => { fetchFiles() }, [])

  async function fetchFiles() {
    try {
      const res = await api.get('/files')
      setFiles(res.data)
    } catch {
      setError('Failed to load files')
    }
  }

  async function handleUpload(e) {
    const file = e.target.files[0]
    if (!file) return
    const form = new FormData()
    form.append('file', file)
    try {
      await api.post('/files/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      await fetchFiles()
    } catch {
      setError('Upload failed')
    }
  }

  function handleDownload(id, name) {
    // Open download URL in current tab — session cookie is included automatically
    window.location.href = `/api/files/${id}/download`
  }

  async function handleShare(id) {
    const target = shareTarget[id]
    if (!target) return
    try {
      await api.post(`/files/${id}/share`, { username: target })
      setShareTarget(prev => ({ ...prev, [id]: '' }))
      await fetchFiles()
    } catch {
      setError('Share failed — user may not exist')
    }
  }

  async function handleLogout() {
    await api.post('/auth/logout')
    navigate('/login')
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h2>My Files</h2>
        <div style={styles.headerActions}>
          <button onClick={() => navigate('/totp-setup')}>🔐 TOTP settings</button>
          <button onClick={handleLogout}>Logout</button>
        </div>
      </div>

      {error && <p style={styles.error}>{error}</p>}

      {/* ── Upload ──────────────────────────────────────────────────── */}
      <div style={styles.section}>
        <button onClick={() => fileInputRef.current.click()}>⬆ Upload file</button>
        <input ref={fileInputRef} type="file" hidden onChange={handleUpload} />
      </div>

      {/* ── File list ───────────────────────────────────────────────── */}
      {files.length === 0 ? (
        <p>No files yet.</p>
      ) : (
        <table style={styles.table}>
          <thead>
            <tr>
              <th>Name</th><th>Owner</th><th>Uploaded</th><th>Shared with</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {files.map(f => (
              <tr key={f.id}>
                <td>{f.originalName}</td>
                <td>{f.owner}</td>
                <td>{new Date(f.uploadedAt).toLocaleString()}</td>
                <td>{f.sharedWith || '—'}</td>
                <td style={styles.actions}>
                  <button onClick={() => handleDownload(f.id, f.originalName)}>⬇ Download</button>
                  <input
                    placeholder="username"
                    value={shareTarget[f.id] || ''}
                    onChange={e => setShareTarget(prev => ({ ...prev, [f.id]: e.target.value }))}
                    style={{ width: 90 }}
                  />
                  <button onClick={() => handleShare(f.id)}>Share</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

const styles = {
  container: { maxWidth: 900, margin: '40px auto', fontFamily: 'sans-serif' },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  headerActions: { display: 'flex', gap: 8 },
  section: { margin: '16px 0' },
  table: { width: '100%', borderCollapse: 'collapse' },
  actions: { display: 'flex', gap: 4, alignItems: 'center' },
  error: { color: 'red' },
}
