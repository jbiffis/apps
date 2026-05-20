// Phone-shell layout: optional app bar, scrollable content, bottom nav.
// Centered with a max width on desktop; full-bleed on mobile (see DESIGN.md).
export default function AppShell({ bar, children, nav }) {
  return (
    <div className="mx-auto flex min-h-full max-w-[480px] flex-col bg-bg">
      {bar}
      <main className="flex-1 overflow-y-auto px-[18px] pb-24">{children}</main>
      {nav}
    </div>
  )
}
