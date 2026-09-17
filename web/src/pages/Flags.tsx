import { useEffect, useState, type FormEvent } from 'react'
import { useAction, useLoad } from '../components/hooks'
import { flags as flagsApi, users as usersApi } from '../api/gatekeeper'
import type { Flag, User } from '../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Slider } from '@/components/ui/slider'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Chip, Empty, Eyebrow, Inspector, InspectorHeader, PageTitle, Panel, Toolbar } from '../components/ui-bits'
import { FieldSelect } from '../components/FieldSelect'
import { cn } from '@/lib/utils'
import { Pager, usePager } from '../components/Pager'

export function FlagsPage() {
  const run = useAction()
  const list = useLoad(flagsApi.list)
  const userList = useLoad(usersApi.list)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const pager = usePager(list.data)
  const [key, setKey] = useState('')
  const [desc, setDesc] = useState('')
  const selected = list.data?.find(f => f.flagKey === selectedKey) ?? null

  async function create(e: FormEvent) {
    e.preventDefault()
    await run(() => flagsApi.create(key, desc || undefined), `Created flag ${key}`, () => { setKey(''); setDesc(''); return list.reload() })
  }

  return (
    <>
      <PageTitle sub="Evaluated per user in this order: environment pin → whitelist → global switch → percentage rollout.">Feature flags</PageTitle>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Panel title={`${list.data?.length ?? '–'} flags`}>
          <form onSubmit={create}>
            <Toolbar className="mb-4">
              <Input id="new-flag-key" className="w-44 font-mono" placeholder="flag-key" value={key} onChange={e => setKey(e.target.value)} required />
              <Input id="new-flag-desc" className="w-56" placeholder="description" value={desc} onChange={e => setDesc(e.target.value)} />
              <Button type="submit">Add flag</Button>
            </Toolbar>
          </form>
          <Table>
            <TableHeader><TableRow><TableHead>Flag</TableHead><TableHead className="w-16">Switch</TableHead><TableHead className="w-28 text-right">Rollout</TableHead><TableHead className="w-10 text-right">v</TableHead><TableHead className="w-20 text-right" /></TableRow></TableHeader>
            <TableBody>
              {pager.slice.map(f => (
                <TableRow key={f.id} aria-selected={selectedKey === f.flagKey} className={cn('cursor-pointer', selectedKey === f.flagKey && 'bg-primary/40 hover:bg-primary/50')} onClick={() => setSelectedKey(f.flagKey)}>
                  <TableCell><code className="font-semibold">{f.flagKey}</code><div className="text-xs text-muted-foreground">{f.description}</div></TableCell>
                  <TableCell>{f.enabled ? <Chip state="on">on</Chip> : <Chip state="off">off</Chip>}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    <div className="flex items-center justify-end gap-2">
                      <span className="inline-block h-2 w-16 border border-foreground"><span className="block h-full bg-primary" style={{ width: `${f.rolloutPercentage}%` }} /></span>
                      {f.rolloutPercentage}%
                    </div>
                  </TableCell>
                  <TableCell className="text-right font-mono tabular-nums">{f.version}</TableCell>
                  <TableCell className="text-right"><Button variant="destructive" size="xs" onClick={e => { e.stopPropagation(); if (confirm(`Delete flag "${f.flagKey}"? It will evaluate to off everywhere.`)) run(() => flagsApi.remove(f.flagKey), 'Flag deleted', () => { setSelectedKey(null); return list.reload() }) }}>Delete</Button></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {list.data?.length === 0 && <Empty>No flags yet — add one above.</Empty>}
          <Pager {...pager} />
        </Panel>
        <Inspector>
          {selected
            ? <FlagDetail key={selected.flagKey} flag={selected} users={userList.data ?? []} onChange={list.reload} />
            : <Empty>Select a flag to edit its rollout, whitelist and environment pins, or evaluate it for a user.</Empty>}
        </Inspector>
      </div>
    </>
  )
}

function FlagDetail({ flag, users, onChange }: { flag: Flag; users: User[]; onChange: () => Promise<void> }) {
  const run = useAction()
  const [enabled, setEnabled] = useState(flag.enabled)
  const [rollout, setRollout] = useState(flag.rolloutPercentage)
  const [desc, setDesc] = useState(flag.description ?? '')
  const [version, setVersion] = useState(flag.version)
  const [userId, setUserId] = useState('')
  const [env, setEnv] = useState('local')
  const [envValue, setEnvValue] = useState('true')
  const [evalUser, setEvalUser] = useState('')
  const [evalResult, setEvalResult] = useState<boolean | null>(null)
  useEffect(() => { setEnabled(flag.enabled); setRollout(flag.rolloutPercentage); setDesc(flag.description ?? ''); setVersion(flag.version) }, [flag])
  const userOptions = users.map(u => ({ value: u.id, label: u.email }))
  const dirty = enabled !== flag.enabled || rollout !== flag.rolloutPercentage || desc !== (flag.description ?? '') || version !== flag.version
  const save = () => run(() => flagsApi.update(flag.flagKey, { enabled, rolloutPercentage: rollout, description: desc, version }), 'Flag saved', onChange)

  return (
    <>
      <InspectorHeader title={<code>{flag.flagKey}</code>} id={flag.id} />
      <form className="grid gap-5" onSubmit={e => { e.preventDefault(); save() }}>
        <div className="flex items-center justify-between gap-3 border-2 border-foreground p-3">
          <div>
            <Label htmlFor="flag-enabled" className="font-semibold">Global switch</Label>
            <p className="text-xs text-muted-foreground">Off means off for everyone except whitelisted users and environment pins.</p>
          </div>
          <Switch id="flag-enabled" checked={enabled} onCheckedChange={setEnabled} />
        </div>
        <div className="grid gap-2">
          <div className="flex items-baseline justify-between">
            <Label htmlFor="flag-rollout">Rollout</Label>
            <span className="font-heading text-2xl leading-none tabular-nums"><span className="bg-primary px-1">{rollout}%</span></span>
          </div>
          <Slider id="flag-rollout" min={0} max={100} value={rollout} onValueChange={v => setRollout(Array.isArray(v) ? v[0] : v)} />
          <p className="text-xs text-muted-foreground">Users are bucketed by a stable hash, so the same user always gets the same answer at a given percentage.</p>
        </div>
        <div className="grid gap-1.5"><Label htmlFor="flag-desc">Description</Label><Input id="flag-desc" value={desc} onChange={e => setDesc(e.target.value)} /></div>
        <div className="grid gap-1.5">
          <Label htmlFor="flag-version">Version <span className="font-normal text-muted-foreground">— server has {flag.version}; change it to force a 409</span></Label>
          <Input id="flag-version" type="number" value={version} onChange={e => setVersion(Number(e.target.value))} className="w-28 tabular-nums" />
        </div>
        <Toolbar><Button type="submit" disabled={!dirty}>Save changes</Button>{!dirty && <span className="text-xs text-muted-foreground">No unsaved changes</span>}</Toolbar>
      </form>

      <Eyebrow className="mt-8">Whitelist — forces on for a user</Eyebrow>
      <Toolbar>
        <FieldSelect value={userId} onChange={setUserId} placeholder="Choose a user" options={userOptions} />
        <Button disabled={!userId} onClick={() => run(() => flagsApi.whitelist(flag.flagKey, userId), 'User whitelisted')}>Whitelist</Button>
        <Button variant="destructive" disabled={!userId} onClick={() => run(() => flagsApi.unwhitelist(flag.flagKey, userId), 'Removed from whitelist')}>Remove</Button>
      </Toolbar>

      <Eyebrow className="mt-6">Environment pin — outranks everything</Eyebrow>
      <Toolbar>
        <Input id="flag-env" className="w-28 font-mono" value={env} onChange={e => setEnv(e.target.value)} placeholder="env" />
        <FieldSelect className="w-28" value={envValue} onChange={setEnvValue} placeholder="value" options={[{ value: 'true', label: 'pin on' }, { value: 'false', label: 'pin off' }]} />
        <Button disabled={!env} onClick={() => run(() => flagsApi.setOverride(flag.flagKey, env, envValue === 'true'), `Pinned in ${env}`)}>Pin</Button>
        <Button variant="destructive" disabled={!env} onClick={() => run(() => flagsApi.removeOverride(flag.flagKey, env), `Pin removed for ${env}`)}>Unpin</Button>
      </Toolbar>
      <p className="mt-1 text-xs text-muted-foreground">Applies when the server's <code>APP_ENVIRONMENT</code> matches (locally: <code>local</code>).</p>

      <Eyebrow className="mt-6">Evaluate</Eyebrow>
      <Toolbar>
        <FieldSelect value={evalUser} onChange={setEvalUser} placeholder="Me" options={userOptions} />
        <Button variant="outline" onClick={() => run(async () => setEvalResult((await flagsApi.evaluate(flag.flagKey, evalUser || undefined)).enabled))}>Evaluate</Button>
        {evalResult !== null && (evalResult ? <Chip state="on">ON</Chip> : <Chip state="off">OFF</Chip>)}
      </Toolbar>
    </>
  )
}
