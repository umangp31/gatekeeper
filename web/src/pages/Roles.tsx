import { useEffect, useState, type FormEvent } from 'react'
import { useAction, useLoad } from '../components/hooks'
import { roles as rolesApi } from '../api/gatekeeper'
import type { Permission, Role } from '../api/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Chip, Empty, PageTitle, SectionTitle } from '../components/ui-bits'
import { FieldSelect } from '../components/FieldSelect'
import { cn } from '@/lib/utils'

export function RolesPage() {
  const run = useAction()
  const list = useLoad(rolesApi.list)
  const catalog = useLoad(rolesApi.catalog)
  const [selected, setSelected] = useState<Role | null>(null)
  const [name, setName] = useState('')
  const [desc, setDesc] = useState('')

  async function create(e: FormEvent) {
    e.preventDefault()
    await run(() => rolesApi.create(name, desc || undefined), `Created role ${name}`, () => { setName(''); setDesc(''); return list.reload() })
  }

  return (
    <>
      <PageTitle>Roles</PageTitle>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="border-2 border-foreground">
          <CardContent>
            <form className="mb-3 flex flex-wrap gap-2" onSubmit={create}>
              <Input placeholder="name" value={name} onChange={e => setName(e.target.value)} required />
              <Input placeholder="description" value={desc} onChange={e => setDesc(e.target.value)} />
              <Button>Create</Button>
            </form>
            <Table>
              <TableHeader><TableRow><TableHead>Name</TableHead><TableHead>Description</TableHead><TableHead /></TableRow></TableHeader>
              <TableBody>
                {list.data?.map(r => (
                  <TableRow key={r.id} className={cn('cursor-pointer', selected?.id === r.id && 'bg-primary/40')} onClick={() => setSelected(r)}>
                    <TableCell>{r.name}<div className="font-mono text-[10px] break-all text-muted-foreground">{r.id}</div></TableCell>
                    <TableCell>{r.description}</TableCell>
                    <TableCell><Button variant="destructive" size="xs" onClick={e => { e.stopPropagation(); if (confirm(`Delete role ${r.name}?`)) run(() => rolesApi.remove(r.id), 'Role deleted', () => { setSelected(null); return list.reload() }) }}>delete</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {list.data?.length === 0 && <Empty>No roles</Empty>}
          </CardContent>
        </Card>
        <Card className="border-2 border-foreground">
          <CardContent>
            {selected
              ? <RoleDetail key={selected.id} role={selected} allRoles={list.data ?? []} catalog={catalog.data ?? []} onChange={list.reload} />
              : <Empty>Select a role to grant permissions and set parent roles.</Empty>}
          </CardContent>
        </Card>
      </div>
      <Card className="mt-4 border-2 border-foreground">
        <CardHeader><CardTitle>Permission catalogue</CardTitle></CardHeader>
        <CardContent>
          <Table>
            <TableHeader><TableRow><TableHead>Code</TableHead><TableHead>Description</TableHead></TableRow></TableHeader>
            <TableBody>{catalog.data?.map(p => <TableRow key={p.id}><TableCell><code>{p.code}</code></TableCell><TableCell>{p.description}</TableCell></TableRow>)}</TableBody>
          </Table>
        </CardContent>
      </Card>
    </>
  )
}

function RoleDetail({ role, allRoles, catalog, onChange }: { role: Role; allRoles: Role[]; catalog: Permission[]; onChange: () => Promise<void> }) {
  const run = useAction()
  const [desc, setDesc] = useState(role.description ?? '')
  const [code, setCode] = useState('')
  const [parentId, setParentId] = useState('')
  // The API does not expose a role's direct permissions/parents; track what we did this session.
  const [granted, setGranted] = useState<string[]>([])
  const [parents, setParents] = useState<Role[]>([])
  useEffect(() => setDesc(role.description ?? ''), [role])

  return (
    <>
      <CardHeader className="px-0 pt-0"><CardTitle>{role.name}</CardTitle><div className="font-mono text-xs text-muted-foreground">{role.id}</div></CardHeader>
      <div className="mb-4 flex flex-wrap gap-2">
        <Input value={desc} onChange={e => setDesc(e.target.value)} placeholder="description" />
        <Button onClick={() => run(() => rolesApi.update(role.id, desc), 'Description saved', onChange)}>Save</Button>
      </div>

      <SectionTitle>Permissions</SectionTitle>
      <div className="mb-2 flex flex-wrap gap-2">
        <FieldSelect value={code} onChange={setCode} placeholder="permission code" options={catalog.map(p => ({ value: p.code, label: p.code }))} />
        <Button disabled={!code} onClick={() => run(() => rolesApi.grant(role.id, code), `Granted ${code}`, () => { setGranted(g => g.includes(code) ? g : [...g, code]); setCode('') })}>Grant</Button>
        <Button variant="destructive" disabled={!code} onClick={() => run(() => rolesApi.revoke(role.id, code), `Revoked ${code}`, () => { setGranted(g => g.filter(c => c !== code)); setCode('') })}>Revoke</Button>
      </div>
      <div>{granted.map(c => <Chip key={c} state="on" onRemove={() => run(() => rolesApi.revoke(role.id, c), `Revoked ${c}`, () => setGranted(g => g.filter(x => x !== c)))}>{c}</Chip>)}</div>
      <p className="mb-4 text-xs text-muted-foreground">Chips reflect grants made this session; verify via a user's effective permissions.</p>

      <SectionTitle>Parent roles (inherits from)</SectionTitle>
      <div className="mb-2 flex flex-wrap gap-2">
        <FieldSelect value={parentId} onChange={setParentId} placeholder="parent role" options={allRoles.filter(r => r.id !== role.id).map(r => ({ value: r.id, label: r.name }))} />
        <Button disabled={!parentId} onClick={() => run(() => rolesApi.addParent(role.id, parentId), 'Parent added', () => {
          const p = allRoles.find(r => r.id === parentId); if (p) setParents(ps => ps.some(x => x.id === p.id) ? ps : [...ps, p]); setParentId('')
        })}>Add parent</Button>
        <Button variant="destructive" disabled={!parentId} onClick={() => run(() => rolesApi.removeParent(role.id, parentId), 'Parent removed', () => { setParents(ps => ps.filter(x => x.id !== parentId)); setParentId('') })}>Remove</Button>
      </div>
      <div>{parents.map(p => <Chip key={p.id} state="on" onRemove={() => run(() => rolesApi.removeParent(role.id, p.id), 'Parent removed', () => setParents(ps => ps.filter(x => x.id !== p.id)))}>{p.name}</Chip>)}</div>
      <p className="text-xs text-muted-foreground">Try adding a cycle (A → B → A): the API rejects it with 409.</p>
    </>
  )
}
