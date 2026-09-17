import { useState } from 'react'
import { request } from '../api/client'
import { flags } from '../api/gatekeeper'
import { useAction, useLoad } from '../components/hooks'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Chip, Empty, Inspector, InspectorHeader, PageTitle, Panel, Toolbar } from '../components/ui-bits'
import { FieldSelect } from '../components/FieldSelect'

/** Raw request console + "all flags for me" bootstrap view. Useful for hitting 403/404 paths on purpose. */
export function PlaygroundPage() {
  const run = useAction()
  const all = useLoad(flags.evaluateAll)
  const [method, setMethod] = useState('GET')
  const [path, setPath] = useState('/api/v1/tenants/me')
  const [body, setBody] = useState('')
  const [out, setOut] = useState<{ status: string; text: string } | null>(null)

  async function send() {
    setOut({ status: '…', text: '' })
    try {
      const parsed = body.trim() ? JSON.parse(body) : undefined
      const res = await request<unknown>(path, { method, body: parsed })
      setOut({ status: res === undefined ? '204' : '200', text: res === undefined ? '(no content)' : JSON.stringify(res, null, 2) })
    } catch (e) {
      const err = e as { status?: number; body?: unknown; message: string }
      setOut({ status: String(err.status ?? 'error'), text: JSON.stringify(err.body ?? err.message, null, 2) })
    }
  }
  const isErr = out && !/^2/.test(out.status) && out.status !== '…'

  return (
    <>
      <PageTitle sub="Send any request with your token attached and read the exact server reply.">Playground</PageTitle>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <Panel title="Request">
          <Toolbar className="mb-2">
            <FieldSelect className="w-28" value={method} onChange={setMethod} placeholder="method" options={['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(m => ({ value: m, label: m }))} />
            <Input id="pg-path" className="min-w-64 flex-1 font-mono" value={path} onChange={e => setPath(e.target.value)} />
            <Button onClick={send}>Send</Button>
          </Toolbar>
          <Textarea id="pg-body" rows={5} className="font-mono text-xs" placeholder='JSON body, e.g. {"flagKey":"x"}' value={body} onChange={e => setBody(e.target.value)} />
          <div className="mt-4 border-2 border-foreground">
            <div className={`flex items-center gap-2 border-b-2 border-foreground px-3 py-1.5 text-xs font-bold uppercase tracking-[0.12em] ${isErr ? 'bg-destructive text-white' : 'bg-primary'}`}>
              Response {out && <span className="font-mono tabular-nums">{out.status}</span>}
            </div>
            <pre className="min-h-28 p-3 font-mono text-xs whitespace-pre-wrap break-all">{out?.text ?? 'Nothing sent yet.'}</pre>
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            Try: <code>GET /api/v1/users/&lt;a user id from another tenant&gt;</code> → 404, not 403. Or a write endpoint as a viewer → 403.
          </p>
        </Panel>
        <Inspector>
          <InspectorHeader title="All flags, evaluated for me" />
          <Toolbar className="mb-3"><Button variant="outline" size="sm" onClick={() => run(all.reload)}>Re-evaluate</Button></Toolbar>
          {all.data && Object.keys(all.data).length === 0 && <Empty>No flags in this tenant.</Empty>}
          {all.data && Object.entries(all.data).map(([k, v]) => (
            <div key={k} className="flex items-center justify-between gap-2 border-b border-foreground/15 py-2">
              <code>{k}</code>{v ? <Chip state="on">ON</Chip> : <Chip state="off">OFF</Chip>}
            </div>
          ))}
        </Inspector>
      </div>
    </>
  )
}
