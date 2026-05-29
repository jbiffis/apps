import { Navigate, useLocation } from 'react-router-dom'
import { isAuthenticated, mustChangePassword } from '../auth.js'

// Gate for authenticated routes. Unauthenticated (or expired-token) visitors
// are sent to /login, remembering where they were headed so login can return
// them there. Users still carrying the force-change flag are held at
// /set-password until they replace the temp password.
export default function RequireAuth({ children }) {
  const location = useLocation()
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  if (mustChangePassword()) {
    return <Navigate to="/set-password" replace />
  }
  return children
}
