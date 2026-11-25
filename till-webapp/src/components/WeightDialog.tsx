import { useState, type FormEvent } from 'react'
import type { Product } from '../api/types'
import { formatPence, parseKgToGrams } from '../money'

const KEYS = ['7', '8', '9', '4', '5', '6', '1', '2', '3', '.', '0', 'C']
const MAX_GRAMS = 30_000

interface Props {
  product: Product
  busy: boolean
  onConfirm: (weightGrams: number) => void
  onCancel: () => void
}

/** Manual weight entry for a loose item without a label: weight in kg on the keypad, priced per kg. */
export function WeightDialog({ product, busy, onConfirm, onCancel }: Props) {
  const [kg, setKg] = useState('')
  const grams = parseKgToGrams(kg)
  const valid = grams !== null && grams <= MAX_GRAMS
  const preview = valid ? Math.round((grams * product.pricePence) / 1000) : null

  const onKey = (key: string) =>
    setKg((value) => {
      if (key === 'C') return ''
      if (key === '.' && value.includes('.')) return value
      const next = value + key
      return /^\d{0,2}(\.\d{0,3})?$/.test(next) ? next : value
    })
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (valid) onConfirm(grams)
  }

  return (
    <div className="weight-backdrop">
      <form className="panel weight-dialog" role="dialog" aria-modal="true" aria-labelledby="weight-title" data-testid="weight-dialog" onSubmit={submit}>
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
            onChange={(e) => /^\d{0,2}(\.\d{0,3})?$/.test(e.target.value) && setKg(e.target.value)}
            autoFocus
          />
          <span className="weight-unit">kg</span>
        </div>
        <div className="weight-preview" data-testid="weight-preview">
          {preview !== null ? `${kg} kg × ${formatPence(product.pricePence)}/kg = ${formatPence(preview)}` : '\u00a0'}
        </div>
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
