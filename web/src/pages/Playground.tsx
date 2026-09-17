import { useState } from 'react'
import { request } from '../api/client'
import { flags } from '../api/gatekeeper'
import { useAction, useLoad } from '../components/hooks'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Chip, Empty, PageTitle } from '../components/ui-bits'
import { FieldSelect } from '../components/FieldSelect'

/** Raw request console + "all flags for me" bootstrap view. Useful for hitting 403/404 paths on purpose. */
export function PlaygroundPage() {
  const run = useAction()
  const all = useLoad(flags.evaluateAll)
  const [method, setMethod] = useState('GET')
  const [path, setPath] = useState('/api/v1/tenants/me')
  const [body, setBody] = useState('')
  const [out, setOut] = useState('')

  async function send() {
    setOut('…')
    try {
      const parsed = body.trim() ? JSON.parse(body) : undefined
      const res = await request<unknown>(path, { method, body: parsed })
      setOut(res === undefined ? '(204 No Content)' : JSON.stringify(res, null, 2))
    } catch (e) {
      const err = e as { status?: number; body?: unknown; message: string }
      setOut(`ERROR ${err.status ?? ''}\n` + JSON.stringify(err.body ?? err.message, null, 2))
    }
  }

  return (
    <>
      <PageTitle>Playground</PageTitle>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="border-2 border-foreground">
          <CardHeader><CardTitle>Raw request (bearer attached)</CardTitle></CardHeader>
          <CardContent>
            <div className="mb-2 flex flex-wrap gap-2">
              <FieldSelect className="w-28" value={method} onChange={setMethod} placeholder="method" options={['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(m => ({ value: m, label: m }))} />
              <Input className="font-mono" value={path} onChange={e => setPath(e.target.value)} />
              <Button onClick={send}>Send</Button>
            </div>
            <Textarea rows={5} className="font-mono text-xs" placeholder="JSON body (optional)" value={body} onChange={e => setBody(e.target.value)} />
            <pre className="mt-2 min-h-24 whitespace-pre-wrap break-all border-2 border-foreground bg-muted p-3 font-mono text-xs">{out}</pre>
            <p className="mt-2 text-xs text-muted-foreground">
              Ideas: <code>GET /api/v1/users/&lt;other-tenant-user-id&gt;</code> → 404 not 403; write endpoints as a viewer → 403.
            </p>
          </CardContent>
        </Card>
        <Card className="border-2 border-foreground">
          <CardHeader><CardTitle>All flags for me <Button variant="ghost" size="xs" onClick={() => run(all.reload)}>refresh</Button></CardTitle></CardHeader>
          <CardContent>
            {all.data && Object.keys(all.data).length === 0 && <Empty>No flags</Empty>}
            {all.data && Object.entries(all.data).map(([k, v]) => (
              <div key={k} className="flex items-center justify-between border-b-2 border-foreground/20 py-2">
                <code>{k}</code>{v ? <Chip state="on">ON</Chip> : <Chip state="off">OFF</Chip>}
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </>
  )
}
