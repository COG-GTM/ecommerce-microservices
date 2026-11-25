import { useState, type FormEvent, type KeyboardEvent } from 'react'
import type { Product } from '../api/types'
import { formatPence, parseKgToGrams } from '../money'

const KEYS = ['7', '8', '9', '4', '5', '6', '1', '2', '3', '.', '0', 'C']
const MAX_GRAMS = 30_000

interface Props {
  product: Product
  busy: boolean
  error: string | null
  onConfirm: (weightGrams: number) => void
  onCancel: () => void
}

/** Manual weight entry for a loose item without a label: weight in kg on the keypad, priced per kg. */
export function WeightDialog({ product, busy, error, onConfirm, onCancel }: Props) {
  const [kg, setKg] = useState('')
  const [submittedKg, setSubmittedKg] = useState<string | null>(null)
  // Typed input that can't be a weight (e.g. a barcode scanned into the field) blocks Add until cleared.
  const [garbled, setGarbled] = useState(false)
  const grams = parseKgToGrams(kg)
  const valid = !garbled && grams !== null && grams <= MAX_GRAMS
  const alert = garbled
    ? 'That is not a weight. Press C and key the weight in kg.'
    : error && kg === submittedKg ? error : null
  const preview = valid ? Math.round((grams * product.pricePence) / 1000) : null

  const onKey = (key: string) => {
    if (key === 'C') setGarbled(false)
    setKg((value) => {
      if (key === 'C') return ''
      if (key === '.' && value.includes('.')) return value
      const next = value + key
      return /^\d{0,2}(\.\d{0,3})?$/.test(next) ? next : value
    })
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!valid) return
    setSubmittedKg(kg)
    onConfirm(grams)
  }

  // Modal: keep Tab inside the dialog so Pay and the rest of the till behind it can't be reached.
  const trapTab = (event: KeyboardEvent<HTMLFormElement>) => {
    if (event.key === 'Escape' && !busy) onCancel()
    if (event.key !== 'Tab') return
    const focusable = [...event.currentTarget.querySelectorAll<HTMLElement>('input, button:not(:disabled)')]
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div className="weight-backdrop">
      <form className="panel weight-dialog" role="dialog" aria-modal="true" aria-labelledby="weight-title" data-testid="weight-dialog" onSubmit={submit} onKeyDown={trapTab}>
        <h3 id="weight-title" data-testid="weight-item">{product.name}</h3>
        <div className="muted" data-testid="weight-price">{formatPence(product.pricePence)}/kg</div>
        <label htmlFor="weight-kg" className="label">Weight (kg)</label>
        <div className="weight-row">
          <input
            id="weight-kg"
            data-testid="weight-input"
            className="barcode"
            inputMode="decimal"
            autoComplete="off"
            placeholder="0.000"
            value={kg}
            onChange={(e) => {
              const next = e.target.value
              if (next === '') {
                setKg('')
                setGarbled(false)
              } else if (/^\d{0,2}(\.\d{0,3})?$/.test(next)) {
                setKg(next)
                setGarbled(false)
              } else {
                setGarbled(true)
              }
            }}
            autoFocus
          />
          <span className="weight-unit">kg</span>
        </div>
        <div className="weight-preview" data-testid="weight-preview">
          {preview !== null ? `${kg} kg × ${formatPence(product.pricePence)}/kg = ${formatPence(preview)}` : '\u00a0'}
        </div>
        {alert && <div className="weight-error" role="alert" data-testid="weight-error">{alert}</div>}
        <div className="keypad">
          {KEYS.map((key) => (
            <button key={key} type="button" className="key" data-testid={`weight-key-${key}`} onClick={() => onKey(key)}>
              {key}
            </button>
          ))}
        </div>
        <div className="weight-actions">
          <button type="button" className="btn outline" data-testid="weight-cancel" disabled={busy} onClick={onCancel}>Cancel</button>
          <button type="submit" className="btn primary" data-testid="weight-confirm" disabled={!valid || busy}>Add item</button>
        </div>
      </form>
    </div>
  )
}
