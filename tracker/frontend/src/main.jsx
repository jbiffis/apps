import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './theme.css'
import { initTheme } from './theme.js'
import App from './App.jsx'

initTheme()

// basename matches the Vite base ('/tracker') so routes are written as plain
// '/login', '/' regardless of the deploy path prefix.
const basename = import.meta.env.BASE_URL.replace(/\/$/, '')

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter basename={basename}>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
