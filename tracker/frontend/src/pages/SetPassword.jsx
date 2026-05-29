import { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { auth as authApi, ApiError } from '../api.js'
import { isAuthenticated, mustChangePassword, setSession } from '../auth.js'
import { Pin } from '../icons/index.jsx'

// Forced first-login password change. Reached after a login whose response
// carried mustChangePassword=true; the app routes here and stays here until
// the user replaces the temp password.
export default function SetPassword() {
  const navigate = useNavigate()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (!isAuthenticated()) return <Navigate to="/login" replace />
  // Nothing to do if the flag is already clear.
  if (!mustChangePassword()) return <Navigate to="/" replace />

  const tooShort = newPassword.length > 0 && newPassword.length < 8
  const mismatch = confirm.length > 0 && newPassword !== confirm
  const canSubmit =
    !busy && currentPassword && newPassword.length >= 8 && newPassword === confirm

  async function onSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setError('')
    setBusy(true)
    try {
      const { token, user, mustChangePassword: stillMust } =
        await authApi.changePassword(currentPassword, newPassword)
      setSession(token, user, stillMust)
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError('Current password is incorrect.')
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
        <h1 className="font-display text-[26px] font-extrabold text-ink">Set your password</h1>
        <p className="mt-1 font-body text-[13px] text-ink-3">
          Choose a new password to finish signing in.
        </p>
      </div>

      <form onSubmit={onSubmit} className="space-y-3">
        <Field
          id="currentPassword"
          label="Current password"
          type="password"
          value={currentPassword}
          onChange={setCurrentPassword}
          autoComplete="current-password"
          autoFocus
        />
        <Field
          id="newPassword"
          label="New password"
          type="password"
          value={newPassword}
          onChange={setNewPassword}
          autoComplete="new-password"
        />
        <Field
          id="confirm"
          label="Confirm new password"
          type="password"
          value={confirm}
          onChange={setConfirm}
          autoComplete="new-password"
        />

        {tooShort && (
          <p className="font-body text-[13px] text-ink-3">
            Password must be at least 8 characters.
          </p>
        )}
        {mismatch && (
          <p className="font-body text-[13px] text-warn">Passwords don't match.</p>
        )}
        {error && (
          <p role="alert" className="font-body text-[13px] text-warn">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={!canSubmit}
          className="w-full rounded-[14px] px-4 py-3.5 font-display text-[15px] font-bold text-white shadow-md transition disabled:opacity-50"
          style={{ background: 'linear-gradient(160deg, var(--accent), var(--accent-deep))' }}
        >
          {busy ? 'Saving…' : 'Save password'}
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
