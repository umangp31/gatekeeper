import { useCallback, useEffect, useState } from 'react'
import { useToast } from './Toast'

/** Load `fn` on mount; expose data + reload. Errors go to the toast. */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const toast = useToast()
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const reload = useCallback(async () => {
    setLoading(true)
    try { setData(await fn()) } catch (e) { toast.error(e) } finally { setLoading(false) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(() => { reload() }, [reload])
  return { data, loading, reload, setData }
}

/** Run an action, toast on error, then call `after` (usually a reload). */
export function useAction() {
  const toast = useToast()
  return async (action: () => Promise<unknown>, okMsg?: string, after?: () => unknown) => {
    try {
      await action()
      if (okMsg) toast.ok(okMsg)
      await after?.()
      return true
    } catch (e) { toast.error(e); return false }
  }
}
