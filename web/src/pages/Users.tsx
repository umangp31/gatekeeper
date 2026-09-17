import { useEffect, useState, type FormEvent } from 'react'
import { useAction, useLoad } from '../components/hooks'
import { roles as rolesApi, users as usersApi } from '../api/gatekeeper'
import type { Role, User } from '../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Chip, ChipGroup, Empty, Eyebrow, Inspector, InspectorHeader, PageTitle, Panel, Toolbar } from '../components/ui-bits'
import { FieldSelect } from '../components/FieldSelect'
import { cn } from '@/lib/utils'
import { Pager, usePager } from '../components/Pager'

export function UsersPage() {
  const run = useAction()
  const list = useLoad(usersApi.list)
  const roleList = useLoad(rolesApi.list)
  const [selected, setSelected] = useState<User | null>(null)
  const pager = usePager(list.data)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  async function create(e: FormEvent) {
    e.preventDefault()
    await run(() => usersApi.create(email, password), `Created ${email}`, () => { setEmail(''); setPassword(''); return list.reload() })
  }

  return (
    <>
      <PageTitle sub="Everyone in this tenant. Select one to assign roles, edit attributes and see what they can do.">Users</PageTitle>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Panel title={`${list.data?.length ?? '–'} users`}>
          <form onSubmit={create}>
            <Toolbar className="mb-4">
              <Input id="new-user-email" className="w-56" placeholder="email" type="email" value={email} onChange={e => setEmail(e.target.value)} required />
              <Input id="new-user-password" className="w-44" placeholder="password (8+)" type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={8} />
              <Button type="submit">Add user</Button>
            </Toolbar>
          </form>
          <Table>
            <TableHeader><TableRow><TableHead className="w-[45%]">Email</TableHead><TableHead className="w-24">Status</TableHead><TableHead>Attributes</TableHead></TableRow></TableHeader>
            <TableBody>
              {pager.slice.map(u => (
                <TableRow key={u.id} aria-selected={selected?.id === u.id} className={cn('cursor-pointer', selected?.id === u.id && 'bg-primary/40 hover:bg-primary/50')} onClick={() => setSelected(u)}>
                  <TableCell><div className="font-semibold">{u.email}</div><div className="font-mono text-[10px] break-all text-muted-foreground">{u.id}</div></TableCell>
                  <TableCell>{u.enabled ? <Chip state="on">active</Chip> : <Chip state="off">disabled</Chip>}</TableCell>
                  <TableCell><code className="text-xs">{JSON.stringify(u.attributes)}</code></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {list.data?.length === 0 && <Empty>No users yet — add one above.</Empty>}
          <Pager {...pager} />
        </Panel>
        <Inspector>
          {selected
            ? <UserDetail user={selected} allRoles={roleList.data ?? []} onChange={async () => { await list.reload(); if (selected) setSelected(await usersApi.get(selected.id)) }} />
            : <Empty>Select a user to manage roles, attributes and see their effective permissions.</Empty>}
        </Inspector>
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

  return (
    <>
      <InspectorHeader title={user.email} id={user.id} />

      <Eyebrow action={<Button variant="ghost" size="xs" onClick={perms.reload}>Refresh</Button>}>Effective permissions</Eyebrow>
      <ChipGroup empty="none — this user can only log in">{perms.data?.map(p => <Chip key={p} state="on">{p}</Chip>)}</ChipGroup>

      <Eyebrow className="mt-6">Roles</Eyebrow>
      <Toolbar>
        <FieldSelect value={roleId} onChange={setRoleId} placeholder="Choose a role" options={allRoles.map(r => ({ value: r.id, label: r.name }))} />
        <Button disabled={!roleId} onClick={() => run(() => usersApi.assignRole(user.id, roleId), 'Role assigned', async () => {
          const r = allRoles.find(x => x.id === roleId); if (r) setAssigned(a => a.some(x => x.id === r.id) ? a : [...a, r])
          setRoleId(''); await after()
        })}>Assign</Button>
        <Button variant="destructive" disabled={!roleId} onClick={() => run(() => usersApi.revokeRole(user.id, roleId), 'Role revoked', after)}>Revoke</Button>
      </Toolbar>
      <div className="mt-2">
        <ChipGroup empty="Roles assigned in this session appear here (the API has no per-user role list).">
          {assigned.map(r => <Chip key={r.id} state="on" onRemove={() => run(() => usersApi.revokeRole(user.id, r.id), 'Role revoked', async () => { setAssigned(a => a.filter(x => x.id !== r.id)); await after() })}>{r.name}</Chip>)}
        </ChipGroup>
      </div>

      <Eyebrow className="mt-6">Attributes (used by attribute rules; embedded in the token)</Eyebrow>
      <Textarea id="user-attrs" rows={5} className="font-mono text-xs" value={attrs} onChange={e => setAttrs(e.target.value)} />
      <Toolbar className="mt-2">
        <Button onClick={() => {
          let parsed: Record<string, unknown>
          try { parsed = JSON.parse(attrs) } catch { return run(() => Promise.reject(new Error('Attributes must be valid JSON, e.g. {"department":"finance"}'))) }
          return run(() => usersApi.updateAttributes(user.id, parsed), 'Attributes saved — takes effect at next login', onChange)
        }}>Save attributes</Button>
      </Toolbar>
    </>
  )
}
