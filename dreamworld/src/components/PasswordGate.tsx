import { useState, type FormEvent, type ReactNode } from 'react'
import { addSong } from '../lib/api'
import { setAdminPassword } from '../lib/auth'

interface PasswordGateProps {
  children: ReactNode
  /** Called when the gate should be unlocked. */
  onUnlock: () => void
}

/**
 * Lock-the-amp-cabinet entry. We verify the password by trying a no-op
 * mutation: a POST that intentionally fails validation but only after
 * auth. 401 → wrong password. 400 (missing_fields) → password OK.
 */
export default function PasswordGate({ children, onUnlock }: PasswordGateProps) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!password) return
    setBusy(true)
    setError('')
    setAdminPassword(password)
    try {
      // Empty body forces a 400 missing_fields when auth passes.
      await addSong('', '')
      // Shouldn't reach here — if it does, the song was added (it won't be).
      onUnlock()
    } catch (err) {
      const e = err as { code?: string; status?: number }
      if (e.code === 'missing_fields' || e.status === 400) {
        // Password accepted.
        onUnlock()
      } else if (e.status === 401) {
        setAdminPassword('')
        setError('Wrong password — try again.')
      } else {
        setError('Server error. Try again in a moment.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {children}
      <div
        style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0, 0, 0, 0.85)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 20,
          zIndex: 100,
        }}
      >
        <form
          onSubmit={onSubmit}
          className="panel pop-in"
          style={{
            padding: 24,
            width: '100%',
            maxWidth: 360,
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
          }}
        >
          <div style={{ textAlign: 'center' }}>
            <span className="brand-plate">Dreamworld</span>
            <h1
              className="serif"
              style={{
                fontSize: 22,
                color: 'var(--cream)',
                marginTop: 18,
                lineHeight: 1.15,
              }}
            >
              Stage door
            </h1>
            <p
              style={{
                fontSize: 12,
                color: 'rgba(244,229,200,0.6)',
                marginTop: 6,
                textTransform: 'uppercase',
                letterSpacing: '0.18em',
              }}
            >
              Password required
            </p>
          </div>
          <input
            type="password"
            className="input"
            placeholder="••••••••"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value)
              if (error) setError('')
            }}
            autoFocus
            autoComplete="current-password"
          />
          {error && (
            <div
              style={{
                fontSize: 12,
                fontWeight: 700,
                color: 'var(--orange-bright)',
                textAlign: 'center',
              }}
            >
              {error}
            </div>
          )}
          <button
            type="submit"
            className="btn btn-primary"
            disabled={busy || !password}
            style={{ justifyContent: 'center', padding: '12px 18px' }}
          >
            {busy ? 'Checking…' : 'Plug in'}
          </button>
        </form>
      </div>
    </>
  )
}
