import { useState, type FormEvent } from 'react'
import { useAuth } from '../components/Auth'
import { useToast } from '../components/Toast'
import { tenants } from '../api/gatekeeper'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

function Field({ label, id, children }: { label: string; id: string; children: React.ReactNode }) {
  return <div className="grid gap-1.5"><Label htmlFor={id}>{label}</Label>{children}</div>
}

export function LoginPage() {
  const { login } = useAuth()
  const toast = useToast()
  const [mode, setMode] = useState<'login' | 'bootstrap'>('login')
  const [slug, setSlug] = useState('acme')
  const [email, setEmail] = useState('admin@acme.test')
  const [password, setPassword] = useState('admin123')
  const [name, setName] = useState('')
  const [bootstrapToken, setBootstrapToken] = useState('local-dev-bootstrap-token')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      if (mode === 'bootstrap') {
        await tenants.bootstrap({ slug, name, adminEmail: email, adminPassword: password }, bootstrapToken)
        toast.ok(`Tenant "${slug}" created`, 'Signing in as its admin…')
      }
      await login(slug, email, password)
    } catch (err) { toast.error(err) } finally { setBusy(false) }
  }

  return (
    <div className="grid min-h-screen place-items-center px-4 py-8">
      <div className="grid w-full max-w-4xl gap-0 border-2 border-foreground md:grid-cols-[1fr_400px]">
        {/* thesis panel */}
        <div className="flex flex-col justify-between gap-8 border-b-2 border-foreground bg-primary p-8 md:border-r-2 md:border-b-0">
          <div className="flex items-center gap-2 font-heading text-lg leading-none">
            <span className="inline-block size-4 bg-foreground" /><span className="inline-block size-4 bg-destructive" /> Gatekeeper
          </div>
          <div>
            <h1 className="font-heading text-4xl leading-[0.95] tracking-tight">Who may do what, and who sees which feature.</h1>
            <p className="mt-4 max-w-prose text-sm leading-relaxed">
              One tenant-scoped service answers both: roles that inherit, attribute rules on the token, and feature flags with
              rollouts, whitelists and environment pins — all changeable at runtime.
            </p>
          </div>
          <dl className="grid grid-cols-3 gap-4 border-t-2 border-foreground pt-4 text-xs">
            <div><dt className="font-bold uppercase tracking-[0.12em]">Access</dt><dd>RBAC + ABAC</dd></div>
            <div><dt className="font-bold uppercase tracking-[0.12em]">Flags</dt><dd>4-rule evaluation</dd></div>
            <div><dt className="font-bold uppercase tracking-[0.12em]">Isolation</dt><dd>404, never 403</dd></div>
          </dl>
        </div>
        {/* form */}
        <div className="bg-background p-8">
          <h2 className="font-heading text-xl leading-none">{mode === 'login' ? 'Sign in' : 'Create a tenant'}</h2>
          <p className="mt-1 mb-6 text-sm text-muted-foreground">{mode === 'login' ? 'Into a tenant, with email and password.' : 'Provisions the tenant and its first admin.'}</p>
          <form className="grid gap-4" onSubmit={submit}>
            <Field label="Tenant slug" id="slug"><Input id="slug" value={slug} onChange={e => setSlug(e.target.value)} required /></Field>
            {mode === 'bootstrap' && <Field label="Tenant name" id="name"><Input id="name" value={name} onChange={e => setName(e.target.value)} required /></Field>}
            <Field label={mode === 'login' ? 'Email' : 'Admin email'} id="email"><Input id="email" type="email" value={email} onChange={e => setEmail(e.target.value)} required /></Field>
            <Field label={mode === 'login' ? 'Password' : 'Admin password (8+ characters)'} id="password"><Input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} required /></Field>
            {mode === 'bootstrap' && <Field label="Bootstrap token" id="token"><Input id="token" value={bootstrapToken} onChange={e => setBootstrapToken(e.target.value)} required /></Field>}
            <Button type="submit" disabled={busy} size="lg" className="w-full">{busy ? 'Working…' : mode === 'login' ? 'Sign in' : 'Create tenant and sign in'}</Button>
          </form>
          <div className="mt-6 flex flex-wrap items-center justify-between gap-2 text-xs">
            <Button variant="link" size="sm" className="px-0" onClick={() => setMode(mode === 'login' ? 'bootstrap' : 'login')}>
              {mode === 'login' ? 'Create a new tenant instead' : 'Back to sign in'}
            </Button>
            {mode === 'login' && <span className="text-muted-foreground">Seeded: <code>acme</code> / <code>admin@acme.test</code></span>}
          </div>
        </div>
      </div>
    </div>
  )
}
