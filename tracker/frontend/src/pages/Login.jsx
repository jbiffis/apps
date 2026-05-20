import { useState } from 'react'
import { useLocation, useNavigate, Navigate } from 'react-router-dom'
import { auth as authApi, ApiError } from '../api.js'
import { isAuthenticated, setSession } from '../auth.js'
import { Pin } from '../icons/index.jsx'

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  // Already signed in → skip the form.
  if (isAuthenticated()) {
    const dest = location.state?.from?.pathname || '/'
    return <Navigate to={dest} replace />
  }

  async function onSubmit(e) {
    e.preventDefault()
    if (busy) return
    setError('')
    setBusy(true)
    try {
      const { token, user } = await authApi.login(username.trim(), password)
      setSession(token, user)
      const dest = location.state?.from?.pathname || '/'
      navigate(dest, { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError('Incorrect username or password.')
      } else {
        setError('Something went wrong. Please try again.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-full max-w-[480px] flex-col justify-center bg-bg px-6">
      <div className="mb-8 flex flex-col items-center text-center">
        <span className="mb-4 grid h-16 w-16 place-items-center rounded-2xl bg-accent text-white">
          <Pin size={30} />
        </span>
        <h1 className="font-display text-[26px] font-extrabold text-ink">LifeTracker</h1>
        <p className="mt-1 font-body text-[13px] text-ink-3">Sign in to your account</p>
      </div>

      <form onSubmit={onSubmit} className="space-y-3">
        <Field
          id="username"
          label="Username"
          value={username}
          onChange={setUsername}
          autoComplete="username"
          autoFocus
        />
        <Field
          id="password"
          label="Password"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />

        {error && (
          <p role="alert" className="font-body text-[13px] text-warn">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={busy || !username || !password}
          className="w-full rounded-[14px] px-4 py-3.5 font-display text-[15px] font-bold text-white shadow-md transition disabled:opacity-50"
          style={{ background: 'linear-gradient(160deg, var(--accent), var(--accent-deep))' }}
        >
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}

function Field({ id, label, type = 'text', value, onChange, ...rest }) {
  return (
    <label htmlFor={id} className="block rounded-field border border-line bg-surface px-4 py-2.5">
      <span className="block font-mono text-[10px] uppercase tracking-[0.08em] text-ink-3">{label}</span>
      <input
        id={id}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full bg-transparent font-display text-[18px] font-bold text-ink outline-none placeholder:text-ink-3"
        {...rest}
      />
    </label>
  )
}
