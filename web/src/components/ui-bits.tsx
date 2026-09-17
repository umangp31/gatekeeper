import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

/** Small shared pieces built on shadcn primitives in the Square Ox style. */
export function PageTitle({ children, sub }: { children: ReactNode; sub?: ReactNode }) {
  return (
    <h1 className="mb-5 flex items-baseline gap-3 text-2xl font-black uppercase tracking-tight">
      <span className="inline-block bg-primary px-2">{children}</span>
      {sub && <span className="text-sm font-medium normal-case tracking-normal text-muted-foreground">{sub}</span>}
    </h1>
  )
}

export function SectionTitle({ children, className }: { children: ReactNode; className?: string }) {
  return <h2 className={cn('mb-2 text-xs font-bold uppercase tracking-widest text-muted-foreground', className)}>{children}</h2>
}

/** Status chip: on = yellow, off = red outline, neutral = black outline. */
export function Chip({ state = 'neutral', children, onRemove }: { state?: 'on' | 'off' | 'neutral' | 'warn'; children: ReactNode; onRemove?: () => void }) {
  return (
    <Badge variant="outline" className={cn(
      'mr-1 mb-1 border-2 font-mono text-[11px]',
      state === 'on' && 'border-foreground bg-primary',
      state === 'off' && 'border-destructive text-destructive',
      state === 'warn' && 'border-destructive bg-destructive text-white',
    )}>
      {children}
      {onRemove && <button type="button" className="ml-1 font-bold hover:text-destructive" onClick={onRemove}>×</button>}
    </Badge>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="border-2 border-dashed border-foreground/40 p-4 text-sm text-muted-foreground">{children}</div>
}

export function Big({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'on' | 'off' | 'neutral' }) {
  return (
    <div className={cn('text-3xl font-black', tone === 'on' && 'bg-primary inline-block px-2', tone === 'off' && 'text-destructive')}>
      {children}
    </div>
  )
}
