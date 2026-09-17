import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

/** Thin wrapper over shadcn Select for the common "pick an id" case. */
export function FieldSelect({ value, onChange, placeholder, options, className }: {
  value: string
  onChange: (v: string) => void
  placeholder: string
  options: { value: string; label: string }[]
  className?: string
}) {
  return (
    <Select value={value || null} onValueChange={v => onChange((v as string | null) ?? '')}>
      <SelectTrigger className={className ?? 'w-56'}><SelectValue placeholder={placeholder} /></SelectTrigger>
      <SelectContent>
        {options.map(o => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
      </SelectContent>
    </Select>
  )
}
