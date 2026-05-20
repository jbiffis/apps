import { Routes, Route, Navigate } from 'react-router-dom'
import RequireAuth from './components/RequireAuth.jsx'
import Login from './pages/Login.jsx'
import Home from './pages/Home.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Home />
          </RequireAuth>
        }
      />
      {/* Unknown paths fall back to home (which itself guards to /login). */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
