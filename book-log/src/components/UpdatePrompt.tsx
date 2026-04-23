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
    <div className="fixed inset-x-0 bottom-20 z-50 mx-auto flex w-[min(calc(100%-2rem),28rem)] items-center justify-between gap-3 rounded-2xl border-4 border-brand-200 bg-white px-4 py-3 shadow-chunky">
      <span className="text-sm font-semibold text-indigo-900">
        ✨ A new version is ready!
      </span>
      <div className="flex gap-2">
        <button
          onClick={() => setNeedRefresh(false)}
          className="rounded-full border-2 border-brand-200 px-3 py-1 text-xs font-bold text-brand-700 hover:bg-brand-50"
        >
          Later
        </button>
        <button
          onClick={() => updateServiceWorker(true)}
          className="rounded-full bg-brand-600 px-3 py-1 text-xs font-bold text-white shadow-chunkySm hover:bg-brand-500"
        >
          Reload
        </button>
      </div>
    </div>
  )
}
