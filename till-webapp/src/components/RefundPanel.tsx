import { useState, type FormEvent } from 'react'
import { TillApiError, tillApi } from '../api/client'
import type { RefundableLine, RefundableSale, RefundResult } from '../api/types'
import { formatPence } from '../money'
import { ApiChip } from './ApiChip'

const errorText = (e: unknown) => (e instanceof TillApiError ? e.message : 'Backend unavailable')

/** Refund mode: find a completed sale by receipt/order number and return selected lines and quantities. */
export function RefundPanel({ tillId, onExit }: { tillId?: string; onExit: () => void }) {
  const [reference, setReference] = useState('')
  const [sale, setSale] = useState<RefundableSale | null>(null)
  const [quantities, setQuantities] = useState<Record<string, number>>({})
  const [result, setResult] = useState<RefundResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const showSale = (next: RefundableSale) => {
    setSale(next)
    setQuantities({})
    setResult(null)
  }

  const lookup = (event?: FormEvent) => {
    event?.preventDefault()
    const ref = reference.trim()
    if (!ref) return
    run(async () => {
      setSale(null)
      showSale(await tillApi.refundLookup(ref))
    })
  }

  const setQty = (skuCode: string, quantity: number) => setQuantities((q) => ({ ...q, [skuCode]: quantity }))

  const selected = sale ? sale.lines.filter((l) => (quantities[l.skuCode] ?? 0) > 0) : []
  // order-service never pays out more than the sale took, so the preview is capped the same way.
  const refundPence = Math.min(sale?.refundablePence ?? 0, selected.reduce((sum, l) => sum + refundPenceFor(l, quantities[l.skuCode]), 0))

  const submit = () =>
    sale &&
    run(async () => {
      const r = await tillApi
        .refund(sale.orderNumber, selected.map((l) => ({ skuCode: l.skuCode, quantity: quantities[l.skuCode] })), tillId)
        .catch(async (e) => {
          // Another till may have refunded these lines: show what is actually left alongside the error.
          await tillApi.refundLookup(sale.orderNumber).then(showSale).catch(() => undefined)
          throw e
        })
      setResult(r)
      if (r.remaining) setSale(r.remaining)
      else await tillApi.refundLookup(sale.orderNumber).then(setSale).catch(() => setSale(null))
      setQuantities({})
    })

  if (result) {
    return (
      <section className="panel refund refund-done" data-testid="refund-complete">
        <h2>Refund complete</h2>
        <div className="refund-amount" data-testid="refund-amount">−{formatPence(result.refundPence)}</div>
        <p className="muted">Return to customer{result.tenderType ? ` by ${result.tenderType.toLowerCase()}` : ''} · original order <span data-testid="refund-original-order">{result.originalOrderNumber}</span></p>
        <p className="order-ref">Refund <span data-testid="refund-number">{result.refundNumber}</span> · {result.transactionNumber}</p>
        <ApiChip>POST /api/till/refund</ApiChip>
        <div className="row-buttons">
          <button type="button" className="btn primary big" data-testid="refund-exit" disabled={busy} onClick={onExit}>Back to sale</button>
          <button type="button" className="btn outline big" data-testid="refund-again" onClick={() => setResult(null)}>Refund more from this receipt</button>
        </div>
      </section>
    )
  }

  return (
    <section className="panel refund" data-testid="refund-panel" aria-label="Refund">
      <div className="refund-head">
        <h2>Refund</h2>
        <div className="panel-head-right">
          <ApiChip>{'GET /api/till/refund/{receipt}'}</ApiChip>
          <button type="button" className="btn outline small" data-testid="refund-exit" disabled={busy} onClick={onExit}>Back to sale</button>
        </div>
      </div>
      <form className="refund-lookup" onSubmit={lookup}>
        <input
          className="barcode"
          data-testid="refund-reference"
          aria-label="Receipt or order number"
          placeholder="Receipt or order number"
          autoComplete="off"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          autoFocus
        />
        <button type="submit" className="btn primary enter" data-testid="refund-lookup" disabled={busy || !reference.trim()}>Find sale</button>
      </form>
      <div className="refund-error" data-testid="refund-error" role="alert">{error ?? '\u00a0'}</div>

      {sale && (
        <>
          <div className="refund-sale" data-testid="refund-sale">
            <span>Order <strong data-testid="refund-sale-order">{sale.orderNumber}</strong></span>
            <span>Paid {formatPence(sale.totalPence)}{sale.tenderType ? ` by ${sale.tenderType.toLowerCase()}` : ''}</span>
            <span>Already refunded <strong data-testid="refund-already">{formatPence(sale.refundedPence)}</strong></span>
          </div>
          <table className="refund-table">
            <thead>
              <tr><th>Item</th><th className="num">Each</th><th className="num">Sold</th><th className="num">Refunded</th><th className="num">Left</th><th className="num">Return</th></tr>
            </thead>
            <tbody>
              {sale.lines.map((l) => {
                const qty = quantities[l.skuCode] ?? 0
                return (
                  <tr key={l.skuCode} data-testid="refund-line">
                    <td data-testid="refund-line-name">{l.name}</td>
                    <td className="num">{formatPence(l.unitPricePence)}</td>
                    <td className="num" data-testid="refund-line-sold">{l.soldQuantity}</td>
                    <td className="num" data-testid="refund-line-refunded">{l.refundedQuantity}</td>
                    <td className="num" data-testid="refund-line-left">{l.refundableQuantity}</td>
                    <td className="num">
                      <span className="refund-qty">
                        <button type="button" className="btn outline" data-testid="refund-qty-minus" aria-label={`Return one fewer ${l.name}`} disabled={busy || qty <= 0} onClick={() => setQty(l.skuCode, qty - 1)}>−</button>
                        <span className="refund-qty-value" data-testid="refund-qty">{qty}</span>
                        <button type="button" className="btn outline" data-testid="refund-qty-plus" aria-label={`Return one more ${l.name}`} disabled={busy || qty >= l.refundableQuantity} onClick={() => setQty(l.skuCode, qty + 1)}>+</button>
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          <div className="refund-foot">
            <div>
              <div className="label">Refund total</div>
              <div className="refund-total" data-testid="refund-total">{formatPence(refundPence)}</div>
            </div>
            <button type="button" className="pay" data-testid="refund-submit" disabled={busy || selected.length === 0} onClick={submit}>
              <span className="pay-label">Refund</span>
              <span className="pay-amount">{formatPence(refundPence)}</span>
            </button>
          </div>
        </>
      )}
    </section>
  )
}

/** Same split as the server: an even share of what was paid, the leftover pennies going to the last units. */
function refundPenceFor(line: RefundableLine, quantity: number): number {
  const share = (units: number) =>
    line.soldQuantity === 0 ? 0 : Math.floor((line.paidPence * Math.min(units, line.soldQuantity)) / line.soldQuantity)
  return share(line.refundedQuantity + quantity) - share(line.refundedQuantity)
}
