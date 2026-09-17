import { useEffect, useState, type FormEvent } from 'react'
import { useAction, useLoad } from '../components/hooks'
import { roles as rolesApi, users as usersApi } from '../api/gatekeeper'
import type { Role, User } from '../api/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Chip, Empty, PageTitle, SectionTitle } from '../components/ui-bits'
import { FieldSelect } from '../components/FieldSelect'
import { cn } from '@/lib/utils'

export function UsersPage() {
  const run = useAction()
  const list = useLoad(usersApi.list)
  const roleList = useLoad(rolesApi.list)
  const [selected, setSelected] = useState<User | null>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  async function create(e: FormEvent) {
    e.preventDefault()
    await run(() => usersApi.create(email, password), `Created ${email}`, () => { setEmail(''); setPassword(''); return list.reload() })
  }

  return (
    <>
      <PageTitle>Users</PageTitle>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="border-2 border-foreground">
          <CardContent>
            <form className="mb-3 flex flex-wrap gap-2" onSubmit={create}>
              <Input placeholder="email" type="email" value={email} onChange={e => setEmail(e.target.value)} required />
              <Input placeholder="password (min 8)" type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={8} />
              <Button>Create</Button>
            </form>
            <Table>
              <TableHeader><TableRow><TableHead>Email</TableHead><TableHead>Enabled</TableHead><TableHead>Attributes</TableHead></TableRow></TableHeader>
              <TableBody>
                {list.data?.map(u => (
                  <TableRow key={u.id} className={cn('cursor-pointer', selected?.id === u.id && 'bg-primary/40')} onClick={() => setSelected(u)}>
                    <TableCell>{u.email}<div className="font-mono text-[10px] break-all text-muted-foreground">{u.id}</div></TableCell>
                    <TableCell>{u.enabled ? <Chip state="on">yes</Chip> : <Chip state="off">no</Chip>}</TableCell>
                    <TableCell><code className="text-xs break-all">{JSON.stringify(u.attributes)}</code></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {list.data?.length === 0 && <Empty>No users</Empty>}
          </CardContent>
        </Card>
        <Card className="border-2 border-foreground">
          <CardContent>
            {selected
              ? <UserDetail user={selected} allRoles={roleList.data ?? []} onChange={async () => { await list.reload(); if (selected) setSelected(await usersApi.get(selected.id)) }} />
              : <Empty>Select a user to manage roles, attributes and see effective permissions.</Empty>}
          </CardContent>
        </Card>
      </div>
    </>
  )
}

function UserDetail({ user, allRoles, onChange }: { user: User; allRoles: Role[]; onChange: () => Promise<void> }) {
  const run = useAction()
  const perms = useLoad(() => usersApi.permissions(user.id), [user.id])
  const [attrs, setAttrs] = useState(JSON.stringify(user.attributes ?? {}, null, 2))
  const [roleId, setRoleId] = useState('')
  useEffect(() => setAttrs(JSON.stringify(user.attributes ?? {}, null, 2)), [user])
  // The API has no "list roles of user" endpoint; we track assignments made in this session.
  const [assigned, setAssigned] = useState<Role[]>([])
  useEffect(() => setAssigned([]), [user.id])
  const after = async () => { await perms.reload(); await onChange() }
  const roleOptions = allRoles.map(r => ({ value: r.id, label: r.name }))

  return (
    <>
      <CardHeader className="px-0 pt-0"><CardTitle>{user.email}</CardTitle><div className="font-mono text-xs text-muted-foreground">{user.id}</div></CardHeader>

      <SectionTitle>Effective permissions <Button variant="ghost" size="xs" onClick={perms.reload}>refresh</Button></SectionTitle>
      <div className="mb-4">{perms.data?.length ? perms.data.map(p => <Chip key={p} state="on">{p}</Chip>) : <span className="text-sm text-muted-foreground">none</span>}</div>

      <SectionTitle>Roles</SectionTitle>
      <div className="mb-2 flex flex-wrap gap-2">
        <FieldSelect value={roleId} onChange={setRoleId} placeholder="pick role" options={roleOptions} />
        <Button disabled={!roleId} onClick={() => run(() => usersApi.assignRole(user.id, roleId), 'Role assigned', async () => {
          const r = allRoles.find(x => x.id === roleId); if (r) setAssigned(a => a.some(x => x.id === r.id) ? a : [...a, r])
          setRoleId(''); await after()
        })}>Assign</Button>
        <Button variant="destructive" disabled={!roleId} onClick={() => run(() => usersApi.revokeRole(user.id, roleId), 'Role revoked', after)}>Revoke</Button>
      </div>
      <div>{assigned.map(r => <Chip key={r.id} state="on" onRemove={() => run(() => usersApi.revokeRole(user.id, r.id), 'Role revoked', async () => { setAssigned(a => a.filter(x => x.id !== r.id)); await after() })}>{r.name}</Chip>)}</div>
      <p className="mb-4 text-xs text-muted-foreground">Chips show roles assigned this session only — the API doesn't expose a user's role list.</p>

      <SectionTitle>Attributes (ABAC)</SectionTitle>
      <Textarea rows={6} className="font-mono text-xs" value={attrs} onChange={e => setAttrs(e.target.value)} />
      <Button className="mt-2" onClick={() => {
        let parsed: Record<string, unknown>
        try { parsed = JSON.parse(attrs) } catch { return run(() => Promise.reject(new Error('Attributes must be valid JSON'))) }
        return run(() => usersApi.updateAttributes(user.id, parsed), 'Attributes saved', onChange)
      }}>Save attributes</Button>
    </>
  )
}
