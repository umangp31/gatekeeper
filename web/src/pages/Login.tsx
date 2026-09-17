import { useState, type FormEvent } from 'react'
import { useAuth } from '../components/Auth'
import { useToast } from '../components/Toast'
import { tenants } from '../api/gatekeeper'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="grid gap-1.5"><Label>{label}</Label>{children}</div>
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
        toast.ok(`Tenant "${slug}" created`, 'Logging in as its admin…')
      }
      await login(slug, email, password)
    } catch (err) { toast.error(err) } finally { setBusy(false) }
  }

  return (
    <div className="grid min-h-screen place-items-center bg-[repeating-linear-gradient(45deg,transparent,transparent_18px,var(--primary)_18px,var(--primary)_20px)]">
      <Card className="w-96 border-4 border-foreground shadow-[8px_8px_0_0_var(--foreground)]">
        <CardHeader>
          <CardTitle className="text-2xl font-black uppercase"><span className="bg-primary px-2">Gatekeeper</span></CardTitle>
          <CardDescription>{mode === 'login' ? 'Sign in to a tenant' : 'Bootstrap a new tenant + admin'}</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4" onSubmit={submit}>
            <Field label="Tenant slug"><Input value={slug} onChange={e => setSlug(e.target.value)} required /></Field>
            {mode === 'bootstrap' && <Field label="Tenant name"><Input value={name} onChange={e => setName(e.target.value)} required /></Field>}
            <Field label={mode === 'login' ? 'Email' : 'Admin email'}><Input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></Field>
            <Field label={mode === 'login' ? 'Password' : 'Admin password (min 8)'}><Input type="password" value={password} onChange={e => setPassword(e.target.value)} required /></Field>
            {mode === 'bootstrap' && <Field label="X-Bootstrap-Token"><Input value={bootstrapToken} onChange={e => setBootstrapToken(e.target.value)} required /></Field>}
            <Button disabled={busy} className="w-full">{mode === 'login' ? 'Sign in' : 'Create tenant & sign in'}</Button>
          </form>
          {mode === 'login' && (
            <p className="mt-4 text-xs text-muted-foreground">
              Tip: create extra users in <b>Users</b> (a viewer with only the <code>viewer</code> role, one with no role),
              then log in as them to see the end-user view change.
            </p>
          )}
          <Button variant="link" className="mt-2 px-0" onClick={() => setMode(mode === 'login' ? 'bootstrap' : 'login')}>
            {mode === 'login' ? 'Bootstrap a new tenant instead' : 'Back to sign in'}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
