import { useState } from 'react'
import { DynamicIcon, Close, Back, Chevron, Plus } from '../icons/index.jsx'

// Hierarchical centered modal for choosing what to log. Categories (e.g. Health
// → Eyes) drill down a level; leaves call onPick. Mount it with a `key` so each
// open starts a fresh navigation stack (no effect-based resets). The "+ New"
// tile (onNew) creates a tracker/category inside the level currently shown —
// rootSlug is the parent for the root level ('' = top level).
export default function TrackerPickerSheet({ rootTitle = 'Log something', rootNodes = [], rootSlug = '', hidden, onPick, onNew, onClose }) {
  const [stack, setStack] = useState([{ title: rootTitle, nodes: rootNodes, slug: rootSlug }])
  const level = stack[stack.length - 1]
  const canGoBack = stack.length > 1
  // Drop hidden leaves (Me-tab prefs); categories always stay so you can drill in.
  const nodes = (level.nodes || []).filter((n) => n.isCategory || !(hidden && hidden.has(n.slug)))

  const choose = (node) =>
    node.isCategory
      ? setStack((s) => [...s, { title: node.name, nodes: node.children || [], slug: node.slug }])
      : onPick(node.slug)
  const goBack = () => setStack((s) => s.slice(0, -1))

  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center p-5" role="dialog" aria-modal="true" aria-label={level.title}>
      <button aria-label="Close" onClick={onClose} className="absolute inset-0 h-full w-full bg-black/40" />
      <div className="relative max-h-[76vh] w-full max-w-[360px] overflow-y-auto rounded-3xl border border-line bg-surface p-4 shadow-2xl">
        <div className="mb-3 flex items-center gap-2">
          {canGoBack && (
            <button onClick={goBack} aria-label="Back"
              className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-line bg-bg text-ink-2">
              <Back size={16} />
            </button>
          )}
          <h2 className="flex-1 font-display text-[18px] font-extrabold text-ink">{level.title}</h2>
          <button onClick={onClose} aria-label="Close"
            className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-line bg-bg text-ink-2">
            <Close size={16} />
          </button>
        </div>

        <div className="grid grid-cols-4 gap-3">
          {nodes.map((node) => (
            <button key={node.slug} onClick={() => choose(node)} className="relative flex flex-col items-center gap-1.5">
              <span className="relative grid h-[54px] w-[54px] place-items-center rounded-2xl border border-line bg-bg text-ink-2">
                <DynamicIcon name={node.icon} size={24} />
                {node.isCategory && (
                  <span className="absolute -bottom-1 -right-1 grid h-4 w-4 place-items-center rounded-full bg-accent text-white">
                    <Chevron size={10} />
                  </span>
                )}
              </span>
              <span className="w-full truncate text-center font-body text-[11px] text-ink-3">{node.name}</span>
            </button>
          ))}
          {onNew && (
            <button onClick={() => onNew(level.slug)} className="flex flex-col items-center gap-1.5">
              <span className="grid h-[54px] w-[54px] place-items-center rounded-2xl border border-dashed border-accent bg-bg text-accent">
                <Plus size={24} />
              </span>
              <span className="w-full truncate text-center font-body text-[11px] text-accent">New</span>
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
