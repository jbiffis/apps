import { useRegisterSW } from 'virtual:pwa-register/react'

export default function UpdatePrompt() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisterError(err) {
      console.error('SW registration failed', err)
    },
  })

  if (!needRefresh) return null

  return (
    <div
      className="card"
      style={{
        position: 'fixed',
        left: '50%',
        transform: 'translateX(-50%)',
        bottom: 'calc(80px + env(safe-area-inset-bottom, 0px))',
        zIndex: 50,
        width: 'min(calc(100% - 24px), 360px)',
        padding: 12,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 10,
      }}
    >
      <span style={{ fontSize: 13, fontWeight: 800 }}>
        ✨ A new version is ready!
      </span>
      <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
        <button
          onClick={() => setNeedRefresh(false)}
          className="btn"
          style={{ padding: '6px 10px', fontSize: 11 }}
        >
          Later
        </button>
        <button
          onClick={() => updateServiceWorker(true)}
          className="btn btn-primary"
          style={{ padding: '6px 10px', fontSize: 11 }}
        >
          Reload
        </button>
      </div>
    </div>
  )
}
