import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from './Auth'
import { useLoad } from './hooks'
import { users } from '../api/gatekeeper'
import { Button } from '@/components/ui/button'
import { InfoButton } from './InfoDialog'
import { cn } from '@/lib/utils'

function Item({ to, end, children, onClick }: { to: string; end?: boolean; children: React.ReactNode; onClick?: () => void }) {
  return (
    <NavLink to={to} end={end} onClick={onClick} className={({ isActive }) => cn(
      'block border-l-4 px-3 py-2 text-sm font-semibold uppercase tracking-wide transition-colors',
      isActive ? 'border-primary bg-primary/30 text-foreground' : 'border-transparent text-foreground/70 hover:border-foreground hover:text-foreground',
    )}>{children}</NavLink>
  )
}

export function Layout() {
  const { session, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const perms = useLoad(() => users.permissions(session!.userId), [session?.userId])
  const can = (p: string) => perms.data?.includes(p) ?? false
  const close = () => setOpen(false)

  const nav = (
    <>
      <Item to="/" end onClick={close}>My app (end user)</Item>
      <div className="mt-4 mb-1 text-[11px] font-bold uppercase tracking-widest text-muted-foreground">Admin console</div>
      <Item to="/overview" onClick={close}>Overview</Item>
      {can('user:read') && <Item to="/users" onClick={close}>Users</Item>}
      {can('role:read') && <Item to="/roles" onClick={close}>Roles</Item>}
      {can('flag:read') && <Item to="/flags" onClick={close}>Feature flags</Item>}
      <Item to="/playground" onClick={close}>Playground</Item>
      <div className="mt-auto border-t-2 border-foreground pt-3 text-xs">
        <div className="font-bold">{session?.tenant?.name ?? 'unknown tenant'}</div>
        <div className="font-mono break-all">{session?.tenant?.slug ?? '?'} · {session?.userId.slice(0, 8)}…</div>
        <div className="mt-1 text-muted-foreground">Admin links hide when you lack <code>*:read</code>.</div>
        <div className="mt-3 grid gap-2">
          <InfoButton />
          <Button variant="destructive" size="sm" className="w-full" onClick={logout}>Log out</Button>
        </div>
      </div>
    </>
  )

  return (
    <div className="flex h-screen flex-col overflow-hidden md:grid md:grid-cols-[240px_minmax(0,1fr)] md:grid-rows-1">
      {/* mobile top bar */}
      <header className="flex items-center justify-between border-b-2 border-foreground px-4 py-3 md:hidden">
        <Logo />
        <Button variant="outline" size="sm" onClick={() => setOpen(o => !o)}>{open ? 'Close' : 'Menu'}</Button>
      </header>
      {open && <nav className="flex max-h-[70vh] flex-col gap-1 overflow-y-auto border-b-2 border-foreground p-4 md:hidden">{nav}</nav>}
      {/* desktop sidebar */}
      <nav className="hidden h-screen flex-col gap-1 overflow-y-auto border-r-2 border-foreground bg-background p-4 md:flex">
        <div className="mb-4"><Logo /></div>
        {nav}
      </nav>
      <main className="min-h-0 min-w-0 flex-1 overflow-x-hidden overflow-y-auto p-4 md:p-6"><Outlet /></main>
    </div>
  )
}

function Logo() {
  return (
    <div className="flex items-center gap-2 text-xl font-black uppercase tracking-tight">
      <span className="inline-block size-5 bg-primary" /><span className="inline-block size-5 bg-destructive" /> Gatekeeper
    </div>
  )
}
