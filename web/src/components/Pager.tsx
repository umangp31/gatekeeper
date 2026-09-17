import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'

/** Client-side pagination over an in-memory list. Keeps tables short so panels never stretch. */
export function usePager<T>(items: T[] | null | undefined, pageSize = 8) {
  const [page, setPage] = useState(0)
  const total = items?.length ?? 0
  const pages = Math.max(1, Math.ceil(total / pageSize))
  useEffect(() => { if (page > pages - 1) setPage(Math.max(0, pages - 1)) }, [pages, page])
  const slice = useMemo(() => (items ?? []).slice(page * pageSize, page * pageSize + pageSize), [items, page, pageSize])
  return { slice, page, pages, total, setPage, pageSize }
}

export function Pager({ page, pages, total, pageSize, setPage }: { page: number; pages: number; total: number; pageSize: number; setPage: (p: number) => void }) {
  if (total <= pageSize) return null
  const from = page * pageSize + 1, to = Math.min(total, (page + 1) * pageSize)
  return (
    <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t-2 border-foreground pt-2 text-xs">
      <span className="tabular-nums text-muted-foreground">{from}–{to} of {total}</span>
      <div className="flex items-center gap-1">
        <Button variant="outline" size="xs" disabled={page === 0} onClick={() => setPage(page - 1)}>Prev</Button>
        <span className="px-2 font-mono tabular-nums">{page + 1} / {pages}</span>
        <Button variant="outline" size="xs" disabled={page >= pages - 1} onClick={() => setPage(page + 1)}>Next</Button>
      </div>
    </div>
  )
}
