import { useEffect, useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Chip } from './ui-bits'

const SEEN_KEY = 'gk.guideSeen'

/** Product guide: what Gatekeeper is, the concepts, and a step-by-step tour. Opens automatically on first visit. */
export function InfoDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[92vh] w-[min(96vw,1500px)] max-w-[96vw] flex-col overflow-hidden border-2 border-foreground p-0 shadow-[8px_8px_0_0_var(--foreground)] sm:max-w-[96vw]">
        <DialogHeader className="border-b-2 border-foreground bg-primary px-6 py-4">
          <DialogTitle className="font-heading text-xl leading-tight">What Gatekeeper is & how to use it</DialogTitle>
          <DialogDescription className="text-foreground/80">Multi-tenant permissions and feature-flag service. This console is a test harness for it.</DialogDescription>
        </DialogHeader>
        <Tabs defaultValue="what" className="flex min-h-0 flex-1 flex-col px-6 pb-6">
          <TabsList className="mt-4 flex-wrap">
            <TabsTrigger value="what">What is it</TabsTrigger>
            <TabsTrigger value="concepts">Concepts</TabsTrigger>
            <TabsTrigger value="tour">Guided tour</TabsTrigger>
            <TabsTrigger value="pages">Pages</TabsTrigger>
            <TabsTrigger value="accounts">Accounts</TabsTrigger>
          </TabsList>
          <div className="mt-4 min-h-0 flex-1 overflow-y-auto pr-2 text-sm leading-relaxed">
            <TabsContent value="what" className="space-y-3">
              <p><b>Gatekeeper</b> is an authorization backend that a product plugs into to answer two questions for every request:</p>
              <ol className="list-decimal space-y-1 pl-5">
                <li><b>Is this user allowed to do this?</b> — role-based access control (roles inherit from parent roles) plus attribute rules on the user's JWT (e.g. <code>department == "finance"</code>).</li>
                <li><b>Should this user see this feature?</b> — feature flags with per-environment pins, per-user whitelists and deterministic percentage rollouts, changeable at runtime without a deploy.</li>
              </ol>
              <p>Everything is <b>tenant-scoped</b>: each customer organisation ("tenant") has its own users, roles and flags, and can never see another tenant's data — cross-tenant lookups return 404, never 403.</p>
              <p className="border-l-4 border-primary bg-primary/20 p-3">
                Think of it as a self-hosted mini <i>Auth0 roles + LaunchDarkly</i>: a product like "Acme Store" calls Gatekeeper to decide which buttons to show and which API calls to accept.
              </p>
              <p>Under the hood: Spring Boot 3 / Java 21, PostgreSQL (effective permissions resolved with a recursive CTE over the role graph), Redis cache with event-driven invalidation, self-issued RS256 JWTs with rotating refresh tokens.</p>
            </TabsContent>

            <TabsContent value="concepts" className="space-y-3">
              <dl className="grid gap-3">
                <Term t="Tenant">An organisation. You log in <i>to</i> a tenant (slug + email + password). Bootstrap a new one from the login screen with the shared <code>X-Bootstrap-Token</code>.</Term>
                <Term t="User">Belongs to one tenant. Has free-form JSON <b>attributes</b> (e.g. <code>{'{"department":"finance"}'}</code>) that are embedded in the JWT and used by ABAC rules.</Term>
                <Term t="Permission">A fixed global catalogue of <code>resource:action</code> codes: <Chip>user:read</Chip><Chip>user:write</Chip><Chip>role:read</Chip><Chip>role:write</Chip><Chip>role:assign</Chip><Chip>flag:read</Chip><Chip>flag:write</Chip><Chip>tenant:admin</Chip></Term>
                <Term t="Role">A named bundle of permissions. A role can have <b>parent roles</b> and inherits every permission of its parents, transitively. Cycles are rejected (409).</Term>
                <Term t="Effective permissions">The union of a user's roles' permissions walked up the whole parent graph. This is what the server checks on every protected endpoint and what the console shows as yellow chips.</Term>
                <Term t="Feature flag">A key like <code>new-checkout</code> evaluated per user, in this order: <b>environment override</b> → <b>whitelist</b> → <b>globally disabled?</b> → <b>percentage rollout</b> (deterministic hash of user+flag, so a user's answer never flips at the same %).</Term>
                <Term t="Optimistic locking">Flags carry a <code>version</code>. Saving with a stale version returns 409 — try it on the Flags page.</Term>
              </dl>
            </TabsContent>

            <TabsContent value="tour" className="space-y-3">
              <p>A 5-minute walkthrough that exercises every part of the system. Start logged in as <code>admin@acme.test</code>.</p>
              <ol className="list-decimal space-y-2 pl-5">
                <li><b>Users →</b> create <code>viewer@acme.test</code> / <code>viewer123</code>. Select it, assign the <code>viewer</code> role, watch <i>Effective permissions</i> fill with the three <code>*:read</code> codes.</li>
                <li>Create <code>nobody@acme.test</code> / <code>nobody123</code> and assign nothing.</li>
                <li><b>Roles →</b> select <code>viewer</code>, grant <code>flag:write</code>. Go back to Users → viewer → refresh: it now has <code>flag:write</code> (cache was invalidated). Revoke it again.</li>
                <li><b>Roles →</b> try to add <code>admin</code> as a parent of <code>viewer</code> <i>and</i> <code>viewer</code> as a parent of <code>admin</code>: the second one is a cycle → 409.</li>
                <li><b>Flags →</b> select <code>new-checkout</code>, drag rollout to 100%, save. Then set the version field to 0 and save again → 409 optimistic lock.</li>
                <li><b>Flags →</b> select <code>beta-dashboard</code> (globally off), whitelist <code>nobody@acme.test</code>.</li>
                <li><b>Users →</b> select <code>nobody</code>, set attributes to <code>{'{"department":"finance"}'}</code>, save.</li>
                <li><b>Log out</b>, log in as <code>nobody@acme.test</code>. The sidebar loses all admin links. On <b>My app</b>: Checkout is <i>New</i>, Dashboard is <i>Beta</i>, Finance reports <i>Visible</i>, every action button disabled — and the red <i>Force</i> buttons get a 403 from the server.</li>
                <li>Log in as <code>viewer@acme.test</code>: view buttons enabled, create buttons disabled, Force create → 403.</li>
                <li>Bonus: bootstrap a second tenant from the login page, copy a user id from tenant A, and in tenant B's <b>Playground</b> run <code>GET /api/v1/users/&lt;that id&gt;</code> → 404 (tenant isolation).</li>
              </ol>
            </TabsContent>

            <TabsContent value="pages" className="space-y-3">
              <dl className="grid gap-3">
                <Term t="My app (end user)">A fake product ("Acme Store") whose sections and buttons are gated by <i>your</i> real permissions and flag evaluations. Best page to see the effect of admin changes.</Term>
                <Term t="Overview">Tenant details, rename (needs <code>tenant:admin</code>), your JWT attributes and effective permissions.</Term>
                <Term t="Users">Create users, assign/revoke roles, edit ABAC attributes, view effective permissions. Requires <code>user:read</code>.</Term>
                <Term t="Roles">Create roles, grant/revoke permissions from the catalogue, add/remove parent roles. Requires <code>role:read</code>.</Term>
                <Term t="Feature flags">Toggle, rollout %, description, version (optimistic lock), whitelist users, pin per environment, evaluate for any user. Requires <code>flag:read</code>.</Term>
                <Term t="Playground">Send any raw request with your bearer token attached and see the exact server response; plus the "all flags for me" bootstrap call.</Term>
              </dl>
              <p className="text-muted-foreground">Sidebar links disappear when you lack the matching <code>*:read</code> permission — but the server enforces it regardless.</p>
            </TabsContent>

            <TabsContent value="accounts" className="space-y-3">
              <p>Seeded tenant <code>acme</code> (start the backend with the <code>seed</code> profile):</p>
              <table className="w-full border-2 border-foreground text-left">
                <thead className="bg-primary text-[11px] uppercase tracking-[0.1em]"><tr><th className="p-2">Email</th><th className="p-2">Password</th><th className="p-2">Role</th><th className="p-2">Sees</th></tr></thead>
                <tbody>
                  <tr className="border-t-2 border-foreground"><td className="p-2"><code>admin@acme.test</code></td><td className="p-2"><code>admin123</code></td><td className="p-2">admin</td><td className="p-2">everything; whitelisted for <code>beta-dashboard</code></td></tr>
                  <tr className="border-t-2 border-foreground"><td className="p-2"><code>viewer@acme.test</code>*</td><td className="p-2"><code>viewer123</code></td><td className="p-2">viewer</td><td className="p-2">read-only admin pages; writes → 403</td></tr>
                  <tr className="border-t-2 border-foreground"><td className="p-2"><code>nobody@acme.test</code>*</td><td className="p-2"><code>nobody123</code></td><td className="p-2">—</td><td className="p-2">only "My app"; everything disabled</td></tr>
                </tbody>
              </table>
              <p className="text-muted-foreground">* not in the seed — create them in <b>Users</b> (step 1–2 of the tour).</p>
              <p>Seeded roles: <Chip state="on">admin</Chip> inherits <Chip state="on">editor</Chip> inherits <Chip state="on">viewer</Chip>. Seeded flags: <code>new-checkout</code> (on, 50%) and <code>beta-dashboard</code> (off, whitelist only).</p>
              <p>Bootstrap token for local dev: <code>local-dev-bootstrap-token</code>. Swagger UI: <a className="underline" href="http://localhost:8080/swagger-ui.html" target="_blank" rel="noreferrer">localhost:8080/swagger-ui.html</a>.</p>
            </TabsContent>
          </div>
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}

function Term({ t, children }: { t: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[140px_1fr] gap-3 border-b-2 border-foreground/15 pb-2 max-sm:grid-cols-1">
      <dt className="font-bold">{t}</dt><dd>{children}</dd>
    </div>
  )
}

/** Button + auto-open on first visit. */
export function InfoButton() {
  const [open, setOpen] = useState(false)
  useEffect(() => {
    try { if (!localStorage.getItem(SEEN_KEY)) { setOpen(true); localStorage.setItem(SEEN_KEY, '1') } } catch { /* ignore */ }
  }, [])
  return (
    <>
      <Button variant="outline" size="sm" className="w-full" onClick={() => setOpen(true)}>Open the guide</Button>
      <InfoDialog open={open} onOpenChange={setOpen} />
    </>
  )
}
