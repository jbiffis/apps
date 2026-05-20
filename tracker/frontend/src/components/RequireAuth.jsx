import { Navigate, useLocation } from 'react-router-dom'
import { isAuthenticated } from '../auth.js'

// Gate for authenticated routes. Unauthenticated (or expired-token) visitors
// are sent to /login, remembering where they were headed so login can return
// them there.
export default function RequireAuth({ children }) {
  const location = useLocation()
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return children
}
