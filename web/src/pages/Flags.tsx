import { useEffect, useState, type FormEvent } from 'react'
import { useAction, useLoad } from '../components/hooks'
import { flags as flagsApi, users as usersApi } from '../api/gatekeeper'
import type { Flag, User } from '../api/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Slider } from '@/components/ui/slider'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Chip, Empty, PageTitle, SectionTitle } from '../components/ui-bits'
import { FieldSelect } from '../components/FieldSelect'
import { cn } from '@/lib/utils'

export function FlagsPage() {
  const run = useAction()
  const list = useLoad(flagsApi.list)
  const userList = useLoad(usersApi.list)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [key, setKey] = useState('')
  const [desc, setDesc] = useState('')
  const selected = list.data?.find(f => f.flagKey === selectedKey) ?? null

  async function create(e: FormEvent) {
    e.preventDefault()
    await run(() => flagsApi.create(key, desc || undefined), `Created flag ${key}`, () => { setKey(''); setDesc(''); return list.reload() })
  }

  return (
    <>
      <PageTitle>Feature flags</PageTitle>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="border-2 border-foreground">
          <CardContent>
            <form className="mb-3 flex flex-wrap gap-2" onSubmit={create}>
              <Input placeholder="flag-key" value={key} onChange={e => setKey(e.target.value)} required />
              <Input placeholder="description" value={desc} onChange={e => setDesc(e.target.value)} />
              <Button>Create</Button>
            </form>
            <Table>
              <TableHeader><TableRow><TableHead>Key</TableHead><TableHead>Enabled</TableHead><TableHead>Rollout</TableHead><TableHead>v</TableHead><TableHead /></TableRow></TableHeader>
              <TableBody>
                {list.data?.map(f => (
                  <TableRow key={f.id} className={cn('cursor-pointer', selectedKey === f.flagKey && 'bg-primary/40')} onClick={() => setSelectedKey(f.flagKey)}>
                    <TableCell><code>{f.flagKey}</code><div className="text-xs text-muted-foreground">{f.description}</div></TableCell>
                    <TableCell>{f.enabled ? <Chip state="on">on</Chip> : <Chip state="off">off</Chip>}</TableCell>
                    <TableCell>{f.rolloutPercentage}%</TableCell>
                    <TableCell className="font-mono">{f.version}</TableCell>
                    <TableCell><Button variant="destructive" size="xs" onClick={e => { e.stopPropagation(); if (confirm(`Delete flag ${f.flagKey}?`)) run(() => flagsApi.remove(f.flagKey), 'Flag deleted', () => { setSelectedKey(null); return list.reload() }) }}>delete</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {list.data?.length === 0 && <Empty>No flags</Empty>}
          </CardContent>
        </Card>
        <Card className="border-2 border-foreground">
          <CardContent>
            {selected
              ? <FlagDetail key={selected.flagKey} flag={selected} users={userList.data ?? []} onChange={list.reload} />
              : <Empty>Select a flag to edit rollout, whitelist and environment overrides.</Empty>}
          </CardContent>
        </Card>
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
  const save = () => run(() => flagsApi.update(flag.flagKey, { enabled, rolloutPercentage: rollout, description: desc, version }), 'Flag saved', onChange)

  return (
    <>
      <CardHeader className="px-0 pt-0"><CardTitle><code>{flag.flagKey}</code></CardTitle></CardHeader>
      <form className="grid gap-4" onSubmit={e => { e.preventDefault(); save() }}>
        <div className="flex items-center gap-3">
          <Switch checked={enabled} onCheckedChange={setEnabled} /><Label>Globally enabled</Label>
        </div>
        <div className="grid gap-2">
          <Label>Rollout <span className="ml-1 bg-primary px-1 font-mono">{rollout}%</span></Label>
          <Slider min={0} max={100} value={rollout} onValueChange={v => setRollout(Array.isArray(v) ? v[0] : v)} />
        </div>
        <div className="grid gap-1.5"><Label>Description</Label><Input value={desc} onChange={e => setDesc(e.target.value)} /></div>
        <div className="grid gap-1.5">
          <Label>Version <span className="text-muted-foreground">(optimistic lock — edit to force a 409; server has {flag.version})</span></Label>
          <Input type="number" value={version} onChange={e => setVersion(Number(e.target.value))} className="w-32" />
        </div>
        <div><Button>Save</Button></div>
      </form>

      <SectionTitle className="mt-6">Whitelist</SectionTitle>
      <div className="flex flex-wrap gap-2">
        <FieldSelect value={userId} onChange={setUserId} placeholder="user" options={userOptions} />
        <Button disabled={!userId} onClick={() => run(() => flagsApi.whitelist(flag.flagKey, userId), 'User whitelisted')}>Add</Button>
        <Button variant="destructive" disabled={!userId} onClick={() => run(() => flagsApi.unwhitelist(flag.flagKey, userId), 'Removed from whitelist')}>Remove</Button>
      </div>

      <SectionTitle className="mt-6">Environment override</SectionTitle>
      <div className="flex flex-wrap gap-2">
        <Input className="w-28" value={env} onChange={e => setEnv(e.target.value)} placeholder="env" />
        <FieldSelect className="w-28" value={envValue} onChange={setEnvValue} placeholder="value" options={[{ value: 'true', label: 'pin ON' }, { value: 'false', label: 'pin OFF' }]} />
        <Button disabled={!env} onClick={() => run(() => flagsApi.setOverride(flag.flagKey, env, envValue === 'true'), `Override set for ${env}`)}>Set</Button>
        <Button variant="destructive" disabled={!env} onClick={() => run(() => flagsApi.removeOverride(flag.flagKey, env), `Override removed for ${env}`)}>Remove</Button>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">Applies when the server's APP_ENVIRONMENT matches (default <code>local</code>).</p>

      <SectionTitle className="mt-6">Evaluate</SectionTitle>
      <div className="flex flex-wrap items-center gap-2">
        <FieldSelect value={evalUser} onChange={setEvalUser} placeholder="me" options={userOptions} />
        <Button onClick={() => run(async () => setEvalResult((await flagsApi.evaluate(flag.flagKey, evalUser || undefined)).enabled))}>Evaluate</Button>
        {evalResult !== null && (evalResult ? <Chip state="on">ON</Chip> : <Chip state="off">OFF</Chip>)}
      </div>
    </>
  )
}
