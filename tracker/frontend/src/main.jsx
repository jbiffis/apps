import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './theme.css'
import { initTheme } from './theme.js'
import App from './App.jsx'

initTheme()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
