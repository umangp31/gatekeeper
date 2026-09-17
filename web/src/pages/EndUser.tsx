import { useState } from 'react'
import { useAuth } from '../components/Auth'
import { useAction, useLoad } from '../components/hooks'
import { flags, roles, users } from '../api/gatekeeper'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Big, Chip, Empty, PageTitle, SectionTitle } from '../components/ui-bits'

/**
 * What a regular end user of a product built on Gatekeeper would see: every section, button
 * and feature below is gated by the *real* effective permissions and flag evaluations of the
 * logged-in user. Log in as different users (viewer / editor / admin, whitelisted or not) and
 * watch the UI change — and click the gated buttons to see the server enforce it too.
 */
export function EndUserPage() {
  const { session } = useAuth()
  const run = useAction()
  const perms = useLoad(() => users.permissions(session!.userId), [session?.userId])
  const myFlags = useLoad(flags.evaluateAll, [session?.userId])
  const [log, setLog] = useState<string[]>([])

  const can = (p: string) => perms.data?.includes(p) ?? false
  const on = (k: string) => myFlags.data?.[k] ?? false
  const attempt = (label: string, fn: () => Promise<unknown>) =>
    run(async () => { await fn(); setLog(l => [`✅ ${label} — allowed`, ...l]) }, undefined)
      .then(ok => { if (!ok) setLog(l => [`⛔ ${label} — server refused`, ...l]) })
  const dept = String(session?.attrs?.department ?? '—')

  const Feature = ({ title, on: isOn, onLabel, offLabel, onText, offText }: { title: string; on: boolean; onLabel: string; offLabel: string; onText: React.ReactNode; offText: React.ReactNode }) => (
    <Card className="border-2 border-foreground">
      <CardHeader><CardTitle className="text-xs uppercase tracking-widest text-muted-foreground">{title}</CardTitle></CardHeader>
      <CardContent>
        <Big tone={isOn ? 'on' : 'off'}>{isOn ? onLabel : offLabel}</Big>
        <p className="mt-2 text-sm">{isOn ? onText : offText}</p>
      </CardContent>
    </Card>
  )

  return (
    <>
      <PageTitle sub="end-user view — everything here is driven by the server's answers for you">Acme Store</PageTitle>
      <Card className="mb-4 border-2 border-foreground">
        <CardContent>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="text-sm">Signed in as <b>{session?.tenant?.slug}</b> user <code>{session?.userId.slice(0, 8)}</code>, department <b>{dept}</b></div>
            <Button variant="outline" size="sm" onClick={() => { perms.reload(); myFlags.reload() }}>Re-evaluate</Button>
          </div>
          <div className="mt-3"><SectionTitle>Permissions</SectionTitle>{perms.data?.length ? perms.data.map(p => <Chip key={p} state="on">{p}</Chip>) : <Chip state="warn">none</Chip>}</div>
          <div className="mt-2"><SectionTitle>Flags</SectionTitle><div className="max-h-24 overflow-y-auto">{myFlags.data && Object.entries(myFlags.data).map(([k, v]) => <Chip key={k} state={v ? 'on' : 'off'}>{k}: {v ? 'on' : 'off'}</Chip>)}</div></div>
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Feature title="Checkout" on={on('new-checkout')} onLabel="New ✨" offLabel="Classic"
          onText={<>You're in the <code>new-checkout</code> rollout bucket. Deterministic: you always land here for this flag.</>}
          offText={<>You're outside the <code>new-checkout</code> rollout (or it's off). Raise the rollout %, whitelist yourself, or pin it via an env override.</>} />
        <Feature title="Dashboard" on={on('beta-dashboard')} onLabel="Beta" offLabel="Not available"
          onText={<>You're whitelisted for <code>beta-dashboard</code> even though it's globally off.</>}
          offText={<>Only whitelisted users see the beta dashboard.</>} />
        <Feature title="Finance reports" on={dept === 'finance'} onLabel="Visible" offLabel="Hidden"
          onText={<>Your JWT <code>attrs.department</code> is <code>finance</code>.</>}
          offText={<>Set this user's attribute <code>{'{"department":"finance"}'}</code> and log in again to see it.</>} />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card className="border-2 border-foreground">
          <CardHeader><CardTitle>Actions</CardTitle></CardHeader>
          <CardContent>
            <p className="mb-3 text-xs text-muted-foreground">Disabled = your effective permissions don't include it. Enabled = try it; the server still checks.</p>
            <div className="mb-2 flex flex-wrap gap-2">
              <Button variant="outline" disabled={!can('user:read')} onClick={() => attempt('List team members (user:read)', users.list)}>View team</Button>
              <Button variant="outline" disabled={!can('flag:read')} onClick={() => attempt('List flags (flag:read)', flags.list)}>View flags</Button>
              <Button variant="outline" disabled={!can('role:read')} onClick={() => attempt('List roles (role:read)', roles.list)}>View roles</Button>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button disabled={!can('flag:write')} onClick={() => attempt('Create flag (flag:write)', () => flags.create(`tmp-${Date.now().toString(36)}`, 'created from end-user demo'))}>Create a flag</Button>
              <Button disabled={!can('user:write')} onClick={() => attempt('Create user (user:write)', () => users.create(`tmp-${Date.now().toString(36)}@acme.test`, 'password123'))}>Invite a user</Button>
              <Button disabled={!can('role:write')} onClick={() => attempt('Create role (role:write)', () => roles.create(`tmp-${Date.now().toString(36)}`))}>Create a role</Button>
            </div>
            <SectionTitle className="mt-6">Force it (bypass the disabled state)</SectionTitle>
            <p className="mb-2 text-xs text-muted-foreground">Simulates a tampered client — the server must answer 403.</p>
            <div className="flex flex-wrap gap-2">
              <Button variant="destructive" onClick={() => attempt('FORCED create flag', () => flags.create(`forced-${Date.now().toString(36)}`))}>Force create flag</Button>
              <Button variant="destructive" onClick={() => attempt('FORCED create user', () => users.create(`forced-${Date.now().toString(36)}@acme.test`, 'password123'))}>Force create user</Button>
              <Button variant="destructive" onClick={() => attempt('FORCED list users', users.list)}>Force list users</Button>
            </div>
          </CardContent>
        </Card>
        <Card className="border-2 border-foreground">
          <CardHeader><CardTitle>Server responses</CardTitle></CardHeader>
          <CardContent>
            {log.length === 0 && <Empty>Click something on the left.</Empty>}
            <div className="max-h-64 overflow-y-auto">
            {log.map((l, i) => <div key={i} className="border-b-2 border-foreground/20 py-1.5 font-mono text-xs">{l}</div>)}
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
