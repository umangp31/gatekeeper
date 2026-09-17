import { useEffect, useState, type FormEvent } from 'react'
import { useAction, useLoad } from '../components/hooks'
import { roles as rolesApi } from '../api/gatekeeper'
import type { Permission, Role } from '../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Chip, ChipGroup, Empty, Eyebrow, Inspector, InspectorHeader, PageTitle, Panel, Toolbar } from '../components/ui-bits'
import { FieldSelect } from '../components/FieldSelect'
import { cn } from '@/lib/utils'
import { Pager, usePager } from '../components/Pager'

export function RolesPage() {
  const run = useAction()
  const list = useLoad(rolesApi.list)
  const catalog = useLoad(rolesApi.catalog)
  const [selected, setSelected] = useState<Role | null>(null)
  const pager = usePager(list.data)
  const [name, setName] = useState('')
  const [desc, setDesc] = useState('')

  async function create(e: FormEvent) {
    e.preventDefault()
    await run(() => rolesApi.create(name, desc || undefined), `Created role ${name}`, () => { setName(''); setDesc(''); return list.reload() })
  }

  return (
    <>
      <PageTitle sub="Roles bundle permissions and inherit from parent roles. A user's effective set is the union up the whole graph.">Roles</PageTitle>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Panel title={`${list.data?.length ?? '–'} roles`}>
          <form onSubmit={create}>
            <Toolbar className="mb-4">
              <Input id="new-role-name" className="w-44" placeholder="name" value={name} onChange={e => setName(e.target.value)} required />
              <Input id="new-role-desc" className="w-56" placeholder="description" value={desc} onChange={e => setDesc(e.target.value)} />
              <Button type="submit">Add role</Button>
            </Toolbar>
          </form>
          <Table>
            <TableHeader><TableRow><TableHead className="w-[40%]">Role</TableHead><TableHead>Description</TableHead><TableHead className="w-20 text-right" /></TableRow></TableHeader>
            <TableBody>
              {pager.slice.map(r => (
                <TableRow key={r.id} aria-selected={selected?.id === r.id} className={cn('cursor-pointer', selected?.id === r.id && 'bg-primary/40 hover:bg-primary/50')} onClick={() => setSelected(r)}>
                  <TableCell><div className="font-semibold">{r.name}</div><div className="font-mono text-[10px] break-all text-muted-foreground">{r.id}</div></TableCell>
                  <TableCell className="text-muted-foreground">{r.description}</TableCell>
                  <TableCell className="text-right"><Button variant="destructive" size="xs" onClick={e => { e.stopPropagation(); if (confirm(`Delete role "${r.name}"? Users holding it lose its permissions.`)) run(() => rolesApi.remove(r.id), 'Role deleted', () => { setSelected(null); return list.reload() }) }}>Delete</Button></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {list.data?.length === 0 && <Empty>No roles yet — add one above.</Empty>}
          <Pager {...pager} />

          <Eyebrow className="mt-8">Permission catalogue (global, fixed)</Eyebrow>
          <Table>
            <TableBody>{catalog.data?.map(p => <TableRow key={p.id}><TableCell className="w-32"><code>{p.code}</code></TableCell><TableCell className="text-muted-foreground">{p.description}</TableCell></TableRow>)}</TableBody>
          </Table>
        </Panel>
        <Inspector>
          {selected
            ? <RoleDetail key={selected.id} role={selected} allRoles={list.data ?? []} catalog={catalog.data ?? []} onChange={list.reload} />
            : <Empty>Select a role to grant permissions and set parent roles.</Empty>}
        </Inspector>
      </div>
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
      <InspectorHeader title={role.name} id={role.id} />
      <Eyebrow>Description</Eyebrow>
      <Toolbar>
        <Input id="role-desc" className="w-64" value={desc} onChange={e => setDesc(e.target.value)} placeholder="What this role is for" />
        <Button onClick={() => run(() => rolesApi.update(role.id, desc), 'Description saved', onChange)}>Save</Button>
      </Toolbar>

      <Eyebrow className="mt-6">Permissions</Eyebrow>
      <Toolbar>
        <FieldSelect value={code} onChange={setCode} placeholder="Choose a permission" options={catalog.map(p => ({ value: p.code, label: p.code }))} />
        <Button disabled={!code} onClick={() => run(() => rolesApi.grant(role.id, code), `Granted ${code}`, () => { setGranted(g => g.includes(code) ? g : [...g, code]); setCode('') })}>Grant</Button>
        <Button variant="destructive" disabled={!code} onClick={() => run(() => rolesApi.revoke(role.id, code), `Revoked ${code}`, () => { setGranted(g => g.filter(c => c !== code)); setCode('') })}>Revoke</Button>
      </Toolbar>
      <div className="mt-2">
        <ChipGroup empty="Grants made in this session appear here; check a user's effective permissions to confirm.">
          {granted.map(c => <Chip key={c} state="on" onRemove={() => run(() => rolesApi.revoke(role.id, c), `Revoked ${c}`, () => setGranted(g => g.filter(x => x !== c)))}>{c}</Chip>)}
        </ChipGroup>
      </div>

      <Eyebrow className="mt-6">Inherits from</Eyebrow>
      <Toolbar>
        <FieldSelect value={parentId} onChange={setParentId} placeholder="Choose a parent role" options={allRoles.filter(r => r.id !== role.id).map(r => ({ value: r.id, label: r.name }))} />
        <Button disabled={!parentId} onClick={() => run(() => rolesApi.addParent(role.id, parentId), 'Parent added', () => {
          const p = allRoles.find(r => r.id === parentId); if (p) setParents(ps => ps.some(x => x.id === p.id) ? ps : [...ps, p]); setParentId('')
        })}>Add parent</Button>
        <Button variant="destructive" disabled={!parentId} onClick={() => run(() => rolesApi.removeParent(role.id, parentId), 'Parent removed', () => { setParents(ps => ps.filter(x => x.id !== parentId)); setParentId('') })}>Remove</Button>
      </Toolbar>
      <div className="mt-2">
        <ChipGroup empty="Parents added in this session appear here. A cycle (A → B → A) is rejected with 409.">
          {parents.map(p => <Chip key={p.id} state="on" onRemove={() => run(() => rolesApi.removeParent(role.id, p.id), 'Parent removed', () => setParents(ps => ps.filter(x => x.id !== p.id)))}>{p.name}</Chip>)}
        </ChipGroup>
      </div>
    </>
  )
}
