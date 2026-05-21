import { Routes, Route, Navigate } from 'react-router-dom'
import RequireAuth from './components/RequireAuth.jsx'
import Login from './pages/Login.jsx'
import Home from './pages/Home.jsx'
import Entry from './pages/Entry.jsx'
import History from './pages/History.jsx'
import Me from './pages/Me.jsx'
import Stats from './pages/Stats.jsx'

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
      <Route
        path="/log/:slug"
        element={
          <RequireAuth>
            <Entry />
          </RequireAuth>
        }
      />
      <Route
        path="/history"
        element={
          <RequireAuth>
            <History />
          </RequireAuth>
        }
      />
      <Route
        path="/me"
        element={
          <RequireAuth>
            <Me />
          </RequireAuth>
        }
      />
      <Route
        path="/stats"
        element={
          <RequireAuth>
            <Stats />
          </RequireAuth>
        }
      />
      {/* Unknown paths fall back to home (which itself guards to /login). */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
