import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

/**
 * Shared pieces in the Square Ox style.
 * Hierarchy rule: lists sit flat on the page under a heavy rule (Panel); only the thing being
 * inspected/edited gets a boxed surface (Inspector). Yellow marks *state/emphasis*, never actions.
 */
export function PageTitle({ children, sub }: { children: ReactNode; sub?: ReactNode }) {
  return (
    <header className="mb-6 flex flex-wrap items-end gap-x-4 gap-y-1">
      <h1 className="font-heading text-3xl leading-none tracking-tight">
        <span className="inline-block bg-primary px-2 pb-1">{children}</span>
      </h1>
      {sub && <p className="max-w-prose text-sm text-muted-foreground">{sub}</p>}
    </header>
  )
}

export function Eyebrow({ children, className, action }: { children: ReactNode; className?: string; action?: ReactNode }) {
  return (
    <div className={cn('mb-2 flex items-center justify-between gap-2', className)}>
      <h2 className="text-[11px] font-bold uppercase tracking-[0.14em] text-muted-foreground">{children}</h2>
      {action}
    </div>
  )
}
export const SectionTitle = Eyebrow

/** Flat list surface: title row over a 2px rule, no box. */
export function Panel({ title, action, children, className }: { title: ReactNode; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={cn('min-w-0', className)}>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b-2 border-foreground pb-2">
        <h2 className="font-heading text-lg leading-none">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

/** Boxed surface for the selected item: the one lifted object on the page. */
export function Inspector({ children, className }: { children: ReactNode; className?: string }) {
  return <aside className={cn('min-w-0 border-2 border-foreground bg-card p-5 shadow-[6px_6px_0_0_var(--primary)]', className)}>{children}</aside>
}

export function InspectorHeader({ title, id }: { title: ReactNode; id?: string }) {
  return (
    <div className="mb-5 border-b-2 border-foreground/15 pb-3">
      <h2 className="font-heading text-xl leading-tight">{title}</h2>
      {id && <div className="mt-1 font-mono text-[11px] break-all text-muted-foreground">{id}</div>}
    </div>
  )
}

/** Status chip: on = yellow fill, off = red outline, warn = red fill, neutral = black outline. */
export function Chip({ state = 'neutral', children, onRemove }: { state?: 'on' | 'off' | 'neutral' | 'warn'; children: ReactNode; onRemove?: () => void }) {
  return (
    <Badge variant="outline" className={cn(
      'border-2 font-mono text-[11px] font-semibold',
      state === 'on' && 'border-foreground bg-primary',
      state === 'off' && 'border-destructive text-destructive',
      state === 'warn' && 'border-destructive bg-destructive text-white',
    )}>
      {children}
      {onRemove && <button type="button" aria-label="remove" className="ml-1 font-bold hover:text-destructive" onClick={onRemove}>×</button>}
    </Badge>
  )
}

/** Chips laid out with gap, never per-chip margins. */
export function ChipGroup({ children, empty = 'none' }: { children: ReactNode; empty?: string }) {
  const arr = Array.isArray(children) ? children.filter(Boolean) : children
  const isEmpty = !arr || (Array.isArray(arr) && arr.length === 0)
  return isEmpty
    ? <span className="text-sm text-muted-foreground">{empty}</span>
    : <div className="flex flex-wrap gap-1.5">{arr}</div>
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="border-2 border-dashed border-foreground/30 p-4 text-sm text-muted-foreground">{children}</div>
}

export function Big({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'on' | 'off' | 'neutral' }) {
  return (
    <div className={cn('font-heading text-3xl leading-none tabular-nums', tone === 'on' && 'inline-block bg-primary px-2 pb-1', tone === 'off' && 'text-destructive')}>
      {children}
    </div>
  )
}

/** Stat tile: eyebrow + big number. Only for figures that are the point. */
export function Stat({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="border-t-2 border-foreground pt-2">
      <div className="text-[11px] font-bold uppercase tracking-[0.14em] text-muted-foreground">{label}</div>
      <div className="mt-1 font-heading text-3xl leading-none tabular-nums">{value}</div>
    </div>
  )
}

/** Toolbar row: controls wrap, share one gap. */
export function Toolbar({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('flex flex-wrap items-center gap-2', className)}>{children}</div>
}
