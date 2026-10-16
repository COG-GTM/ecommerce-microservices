import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { initAuth } from './api/auth'

const root = createRoot(document.getElementById('root')!)

initAuth()
  .then(() => {
    root.render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
  })
  .catch((error: unknown) => {
    console.error('Keycloak login failed', error)
    root.render(<p role="alert">Sign-in failed. Check that Keycloak is running and reload.</p>)
  })
