import { useState, type FormEvent } from 'react'
import type { Basket, BasketLine, Product } from '../api/types'
import { formatPence } from '../money'
import { ApiChip } from './ApiChip'
import { Keypad } from './Keypad'

interface Props {
  basket: Basket | null
  quickKeys: Product[]
  selectedLine: BasketLine | null
  message: { kind: 'ok' | 'error'; text: string } | null
  busy: boolean
  onScan: (barcode: string) => void
  onQty: (delta: number) => void
  onVoidLine: () => void
  onVoidSale: () => void
  onPay: () => void
}

export function SellPanel(props: Props) {
  const { basket, quickKeys, selectedLine, message, busy } = props
  const [barcode, setBarcode] = useState('')

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const code = barcode.trim()
    if (!code) return
    props.onScan(code)
    setBarcode('')
  }
  const onKey = (key: string) => setBarcode((value) => (key === 'C' ? '' : value + key))
  const hasLines = (basket?.lines.length ?? 0) > 0

  return (
    <>
      <section className="panel scan" aria-label="Scan">
        <div className="panel-head compact">
          <label htmlFor="barcode" className="label">Scan or key a barcode</label>
          <ApiChip>{'GET /api/till/products/{barcode}'}</ApiChip>
        </div>
        <form className="scan-row" onSubmit={submit}>
          <input
            id="barcode"
            data-testid="barcode-input"
            className="barcode"
            inputMode="numeric"
            autoComplete="off"
            placeholder="e.g. 1005"
            value={barcode}
            onChange={(e) => setBarcode(e.target.value)}
            autoFocus
          />
          <button type="submit" className="btn primary enter" data-testid="enter" disabled={busy}>Enter</button>
        </form>
        <div className={'scan-msg ' + (message?.kind ?? '')} data-testid="scan-message" role="status">
          {message?.text ?? '\u00a0'}
        </div>
      </section>

      <section className="panel quick" aria-label="Quick keys">
        <div className="panel-head compact">
          <h3 className="label">Quick keys</h3>
          <ApiChip>GET /api/till/products?quickkey=true</ApiChip>
        </div>
        <div className="quick-grid">
          {quickKeys.map((p) => (
            <button
              key={p.barcode}
              type="button"
              className="quick-key"
              data-testid={`quickkey-${p.barcode}`}
              disabled={busy}
              onClick={() => props.onScan(p.barcode)}
            >
              <span className="qk-name">{p.name}</span>
              <span className="qk-meta">{formatPence(p.pricePence)} · {p.barcode}</span>
            </button>
          ))}
        </div>
      </section>

      <div className="controls">
        <section className="panel keypad-panel" aria-label="Keypad">
          <Keypad id="key" onKey={onKey} />
        </section>
        <div className="line-actions">
          <button type="button" className="btn outline" data-testid="qty-plus" disabled={!selectedLine || busy} onClick={() => props.onQty(1)}>Qty +1</button>
          <button type="button" className="btn outline" data-testid="qty-minus" disabled={!selectedLine || selectedLine.quantity <= 1 || busy} onClick={() => props.onQty(-1)}>Qty −1</button>
          <button type="button" className="btn danger" data-testid="void-line" disabled={!selectedLine || busy} onClick={props.onVoidLine}>Void line</button>
          <button type="button" className="btn danger" data-testid="void-sale" disabled={!hasLines || busy} onClick={props.onVoidSale}>Void sale</button>
        </div>
        <button type="button" className="pay" data-testid="pay" disabled={!hasLines || busy} onClick={props.onPay}>
          <span className="pay-label">Pay</span>
          <span className="pay-amount">{formatPence(basket?.totalPence ?? 0)}</span>
        </button>
      </div>
    </>
  )
}
