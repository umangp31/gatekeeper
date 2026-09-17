import { useState } from 'react'
import { useAuth } from '../components/Auth'
import { useAction, useLoad } from '../components/hooks'
import { flags, roles, tenants, users } from '../api/gatekeeper'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableRow } from '@/components/ui/table'
import { Big, Chip, PageTitle } from '../components/ui-bits'

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
      <PageTitle>Overview</PageTitle>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {(['users', 'roles', 'flags'] as const).map(k => (
          <Card key={k} className="border-2 border-foreground">
            <CardHeader><CardTitle className="text-xs uppercase tracking-widest text-muted-foreground">{k}</CardTitle></CardHeader>
            <CardContent><Big>{counts.data?.[k] ?? '–'}</Big></CardContent>
          </Card>
        ))}
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card className="border-2 border-foreground">
          <CardHeader><CardTitle>Current tenant</CardTitle></CardHeader>
          <CardContent>
            <Table>
              <TableBody>
                <TableRow><TableHead>Name</TableHead><TableCell>{session?.tenant?.name}</TableCell></TableRow>
                <TableRow><TableHead>Slug</TableHead><TableCell className="font-mono">{session?.tenant?.slug}</TableCell></TableRow>
                <TableRow><TableHead>Id</TableHead><TableCell className="font-mono text-xs break-all">{session?.tenant?.id}</TableCell></TableRow>
                <TableRow><TableHead>Status</TableHead><TableCell><Chip state="on">{session?.tenant?.status}</Chip></TableCell></TableRow>
              </TableBody>
            </Table>
            <div className="mt-3 flex flex-wrap gap-2">
              <Input placeholder="New tenant name" value={name} onChange={e => setName(e.target.value)} />
              <Button disabled={!name} onClick={() => run(() => tenants.rename(name), 'Tenant renamed', () => { setName(''); return refreshTenant() })}>
                Rename
              </Button>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">Requires <code>tenant:admin</code>.</p>
          </CardContent>
        </Card>
        <Card className="border-2 border-foreground">
          <CardHeader><CardTitle>My session</CardTitle></CardHeader>
          <CardContent>
            <Table>
              <TableBody>
                <TableRow><TableHead>User id</TableHead><TableCell className="font-mono text-xs break-all">{session?.userId}</TableCell></TableRow>
                <TableRow><TableHead>JWT attrs</TableHead><TableCell><code className="text-xs">{JSON.stringify(session?.attrs ?? {})}</code></TableCell></TableRow>
                <TableRow><TableHead>Effective permissions</TableHead><TableCell>
                  {me.data?.length ? me.data.map(p => <Chip key={p}>{p}</Chip>) : <span className="text-muted-foreground">none</span>}
                </TableCell></TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
