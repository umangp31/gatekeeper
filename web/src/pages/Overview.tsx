import { useState } from 'react'
import { useAuth } from '../components/Auth'
import { useAction, useLoad } from '../components/hooks'
import { flags, roles, tenants, users } from '../api/gatekeeper'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Chip, ChipGroup, Eyebrow, Inspector, InspectorHeader, PageTitle, Panel, Stat, Toolbar } from '../components/ui-bits'

export function OverviewPage() {
  const { session, refreshTenant } = useAuth()
  const run = useAction()
  const me = useLoad(() => users.permissions(session!.userId), [session?.userId])
  const counts = useLoad(async () => {
    const [u, r, f] = await Promise.all([users.list(), roles.list(), flags.list()])
    return { users: u.length, roles: r.length, flags: f.length }
  })
  const [name, setName] = useState('')

  return (
    <>
      <PageTitle sub="The tenant your token belongs to, and what it lets you do.">Overview</PageTitle>
      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Users" value={counts.data?.users ?? '–'} />
        <Stat label="Roles" value={counts.data?.roles ?? '–'} />
        <Stat label="Feature flags" value={counts.data?.flags ?? '–'} />
      </div>
      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <Panel title="Tenant">
          <dl className="grid grid-cols-[110px_1fr] gap-y-2 text-sm">
            <dt className="text-muted-foreground">Name</dt><dd className="font-semibold">{session?.tenant?.name}</dd>
            <dt className="text-muted-foreground">Slug</dt><dd className="font-mono">{session?.tenant?.slug}</dd>
            <dt className="text-muted-foreground">Id</dt><dd className="font-mono text-xs break-all">{session?.tenant?.id}</dd>
            <dt className="text-muted-foreground">Status</dt><dd><Chip state="on">{session?.tenant?.status}</Chip></dd>
          </dl>
          <Eyebrow className="mt-5">Rename tenant</Eyebrow>
          <Toolbar>
            <Input id="tenant-name" className="max-w-xs" placeholder="New name" value={name} onChange={e => setName(e.target.value)} />
            <Button disabled={!name} onClick={() => run(() => tenants.rename(name), 'Tenant renamed', () => { setName(''); return refreshTenant() })}>Rename</Button>
          </Toolbar>
          <p className="mt-1 text-xs text-muted-foreground">Needs <code>tenant:admin</code>; anyone else gets a 403.</p>
        </Panel>
        <Inspector>
          <InspectorHeader title="Your session" id={session?.userId} />
          <Eyebrow>Token attributes</Eyebrow>
          <code className="block text-xs">{JSON.stringify(session?.attrs ?? {})}</code>
          <Eyebrow className="mt-5">Effective permissions</Eyebrow>
          <ChipGroup>{me.data?.map(p => <Chip key={p} state="on">{p}</Chip>)}</ChipGroup>
        </Inspector>
      </div>
    </>
  )
}
