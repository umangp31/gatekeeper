import { useState } from 'react'
import { useAuth } from '../components/Auth'
import { useAction, useLoad } from '../components/hooks'
import { flags, roles, users } from '../api/gatekeeper'
import { Button } from '@/components/ui/button'
import { Chip, ChipGroup, Empty, Eyebrow, Inspector, InspectorHeader, PageTitle, Panel, Toolbar } from '../components/ui-bits'
import { cn } from '@/lib/utils'

/**
 * What a regular end user of a product built on Gatekeeper would see: every section, button
 * and feature below is gated by the *real* effective permissions and flag evaluations of the
 * logged-in user. Log in as different users and watch the UI change — then click the gated
 * buttons to see the server enforce it too.
 */
export function EndUserPage() {
  const { session } = useAuth()
  const run = useAction()
  const perms = useLoad(() => users.permissions(session!.userId), [session?.userId])
  const myFlags = useLoad(flags.evaluateAll, [session?.userId])
  const [log, setLog] = useState<{ ok: boolean; text: string }[]>([])

  const can = (p: string) => perms.data?.includes(p) ?? false
  const on = (k: string) => myFlags.data?.[k] ?? false
  const attempt = (label: string, fn: () => Promise<unknown>) =>
    run(async () => { await fn(); setLog(l => [{ ok: true, text: `${label} — allowed` }, ...l]) }, undefined)
      .then(ok => { if (!ok) setLog(l => [{ ok: false, text: `${label} — refused by server` }, ...l]) })
  const dept = String(session?.attrs?.department ?? '—')

  return (
    <>
      <PageTitle sub="A product built on Gatekeeper. Everything here is what the server says you may see and do.">Acme Store</PageTitle>

      <div className="mb-6 flex flex-wrap items-center justify-between gap-3 border-y-2 border-foreground py-3">
        <div className="text-sm">Signed in to <b>{session?.tenant?.slug}</b> as <code>{session?.userId.slice(0, 8)}</code> · department <b>{dept}</b></div>
        <Button variant="outline" size="sm" onClick={() => { perms.reload(); myFlags.reload() }}>Re-evaluate</Button>
      </div>

      {/* Features: one composed row, same structure in each cell */}
      <div className="grid gap-px border-2 border-foreground bg-foreground sm:grid-cols-3">
        <Feature title="Checkout" on={on('new-checkout')} onLabel="New" offLabel="Classic" gate="new-checkout · rollout"
          onText="You fell inside the rollout bucket. That's deterministic — you'll always land here at this percentage."
          offText="Outside the rollout (or it's switched off). Raise the percentage, whitelist yourself, or pin it for this environment." />
        <Feature title="Dashboard" on={on('beta-dashboard')} onLabel="Beta" offLabel="Not available" gate="beta-dashboard · whitelist"
          onText="You're whitelisted, so you see it even though the flag is globally off."
          offText="Only whitelisted users see the beta dashboard." />
        <Feature title="Finance reports" on={dept === 'finance'} onLabel="Visible" offLabel="Hidden" gate="attrs.department == finance"
          onText="Your token carries department = finance."
          offText={'Set this user\'s attribute {"department":"finance"} and sign in again.'} />
      </div>

      <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Panel title="Actions">
          <Eyebrow>Read</Eyebrow>
          <Toolbar className="mb-4">
            <Gated can={can('user:read')} perm="user:read" onClick={() => attempt('View team (user:read)', users.list)}>View team</Gated>
            <Gated can={can('flag:read')} perm="flag:read" onClick={() => attempt('View flags (flag:read)', flags.list)}>View flags</Gated>
            <Gated can={can('role:read')} perm="role:read" onClick={() => attempt('View roles (role:read)', roles.list)}>View roles</Gated>
          </Toolbar>
          <Eyebrow>Write</Eyebrow>
          <Toolbar className="mb-6">
            <Gated can={can('flag:write')} perm="flag:write" onClick={() => attempt('Create flag (flag:write)', () => flags.create(`tmp-${Date.now().toString(36)}`, 'created from Acme Store'))}>Create a flag</Gated>
            <Gated can={can('user:write')} perm="user:write" onClick={() => attempt('Invite user (user:write)', () => users.create(`tmp-${Date.now().toString(36)}@acme.test`, 'password123'))}>Invite a user</Gated>
            <Gated can={can('role:write')} perm="role:write" onClick={() => attempt('Create role (role:write)', () => roles.create(`tmp-${Date.now().toString(36)}`))}>Create a role</Gated>
          </Toolbar>
          <Eyebrow>Tampered client — buttons never disabled</Eyebrow>
          <p className="mb-2 text-xs text-muted-foreground">The UI gate is a courtesy. The server must refuse these with 403 unless you actually hold the permission.</p>
          <Toolbar>
            <Button variant="destructive" size="sm" onClick={() => attempt('Force create flag', () => flags.create(`forced-${Date.now().toString(36)}`))}>Force create flag</Button>
            <Button variant="destructive" size="sm" onClick={() => attempt('Force create user', () => users.create(`forced-${Date.now().toString(36)}@acme.test`, 'password123'))}>Force create user</Button>
            <Button variant="destructive" size="sm" onClick={() => attempt('Force list users', users.list)}>Force list users</Button>
          </Toolbar>
        </Panel>
        <Inspector>
          <InspectorHeader title="What the server says about you" />
          <Eyebrow>Effective permissions</Eyebrow>
          <ChipGroup empty="none — you can only sign in">{perms.data?.map(p => <Chip key={p} state="on">{p}</Chip>)}</ChipGroup>
          <Eyebrow className="mt-5">Flags</Eyebrow>
          <div>
            <ChipGroup>{myFlags.data && Object.entries(myFlags.data).map(([k, v]) => <Chip key={k} state={v ? 'on' : 'off'}>{k} {v ? 'on' : 'off'}</Chip>)}</ChipGroup>
          </div>
          <Eyebrow className="mt-5">Responses</Eyebrow>
          {log.length === 0 && <Empty>Press an action to see the server's verdict.</Empty>}
          <div>
            {log.map((l, i) => (
              <div key={i} className={cn('flex gap-2 border-b border-foreground/15 py-1.5 font-mono text-xs', !l.ok && 'text-destructive')}>
                <span className={cn('mt-1 inline-block size-2 shrink-0', l.ok ? 'bg-primary' : 'bg-destructive')} />{l.text}
              </div>
            ))}
          </div>
        </Inspector>
      </div>
    </>
  )
}

function Feature({ title, on, onLabel, offLabel, gate, onText, offText }: { title: string; on: boolean; onLabel: string; offLabel: string; gate: string; onText: string; offText: string }) {
  return (
    <div className="flex min-h-44 flex-col bg-background p-5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] font-bold uppercase tracking-[0.14em] text-muted-foreground">{title}</span>
        <span className={cn('size-3', on ? 'bg-primary outline-2 outline-foreground' : 'bg-destructive')} aria-hidden />
      </div>
      <div className={cn('mt-3 font-heading text-3xl leading-none', on ? 'inline-block self-start bg-primary px-2 pb-1' : 'text-destructive')}>{on ? onLabel : offLabel}</div>
      <p className="mt-3 text-sm leading-relaxed">{on ? onText : offText}</p>
      <div className="mt-auto pt-3 font-mono text-[11px] text-muted-foreground">{gate}</div>
    </div>
  )
}

function Gated({ can, perm, onClick, children }: { can: boolean; perm: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <span className="inline-flex flex-col gap-0.5">
      <Button variant={can ? 'default' : 'outline'} disabled={!can} onClick={onClick}>{children}</Button>
      {!can && <span className="font-mono text-[10px] text-destructive">needs {perm}</span>}
    </span>
  )
}
