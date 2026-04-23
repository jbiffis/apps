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
    <div className="fixed inset-x-0 bottom-4 z-50 mx-auto flex w-[min(calc(100%-2rem),28rem)] items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-lg">
      <span className="text-sm text-slate-700">New version available.</span>
      <div className="flex gap-2">
        <button
          onClick={() => setNeedRefresh(false)}
          className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
        >
          Later
        </button>
        <button
          onClick={() => updateServiceWorker(true)}
          className="rounded-md bg-slate-900 px-2.5 py-1 text-xs font-medium text-white hover:bg-slate-800"
        >
          Reload
        </button>
      </div>
    </div>
  )
}
