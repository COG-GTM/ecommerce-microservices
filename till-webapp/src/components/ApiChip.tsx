import { createContext, useContext } from 'react'

/** Presenter mode (?api=1) shows which backend endpoint each panel calls. */
export const ApiChipsContext = createContext(false)

export function ApiChip({ children }: { children: string }) {
  const visible = useContext(ApiChipsContext)
  if (!visible) return null
  return <code className="api-chip" data-testid="api-chip">{children}</code>
}
