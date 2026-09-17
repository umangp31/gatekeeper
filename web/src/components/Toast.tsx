import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { cn } from '@/lib/utils'

type Kind = 'ok' | 'err' | 'info'
interface Toast { id: number; kind: Kind; title: string; detail?: string }

const Ctx = createContext<{ push: (kind: Kind, title: string, detail?: string) => void }>({ push: () => {} })

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([])
  const push = useCallback((kind: Kind, title: string, detail?: string) => {
    const id = Date.now() + Math.random()
    setItems(t => [...t, { id, kind, title, detail }])
    setTimeout(() => setItems(t => t.filter(x => x.id !== id)), 5000)
  }, [])
  return (
    <Ctx.Provider value={{ push }}>
      {children}
      <div className="fixed right-4 bottom-4 z-50 flex flex-col gap-2">
        {items.map(t => (
          <div key={t.id} className={cn(
            'min-w-64 max-w-md border-2 border-foreground bg-background px-4 py-3 text-sm shadow-[4px_4px_0_0_var(--foreground)]',
            t.kind === 'err' && 'border-destructive text-destructive shadow-[4px_4px_0_0_var(--destructive)]',
            t.kind === 'ok' && 'shadow-[4px_4px_0_0_var(--primary)]',
          )}>
            <div className="font-semibold">{t.title}</div>
            {t.detail && <div className="opacity-80">{t.detail}</div>}
          </div>
        ))}
      </div>
    </Ctx.Provider>
  )
}

export function useToast() {
  const { push } = useContext(Ctx)
  return {
    ok: (title: string, detail?: string) => push('ok', title, detail),
    info: (title: string, detail?: string) => push('info', title, detail),
    error: (e: unknown, fallback = 'Request failed') => {
      if (e instanceof ApiError) push('err', `${e.status} ${e.body?.title ?? ''}`.trim(), e.message)
      else push('err', fallback, e instanceof Error ? e.message : String(e))
    },
  }
}
