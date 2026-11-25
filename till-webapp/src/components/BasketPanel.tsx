import { Fragment, useEffect, useRef } from 'react'
import type { Adjustment, Basket } from '../api/types'
import { formatKg, formatPence } from '../money'
import { ApiChip } from './ApiChip'
import { VatSummary } from './VatSummary'

interface Props {
  basket: Basket | null
  selectedLineId: string | null
  onSelect: (lineId: string) => void
}

export function BasketPanel({ basket, selectedLineId, onSelect }: Props) {
  const lines = basket?.lines ?? []
  const count = basket?.itemCount ?? 0
  const savingsByLine = new Map<string, Adjustment[]>()
  for (const a of basket?.adjustments ?? []) {
    if (a.lineId && a.amountPence < 0) savingsByLine.set(a.lineId, [...(savingsByLine.get(a.lineId) ?? []), a])
  }
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    scrollRef.current?.querySelector('tr.selected')?.scrollIntoView({ block: 'nearest' })
  }, [selectedLineId, basket])
  return (
    <section className="panel basket" aria-label="Basket">
      <div className="panel-head">
        <h2>Basket</h2>
        <div className="panel-head-right">
          <ApiChip>POST /api/till/basket/lines</ApiChip>
          <span className="muted" data-testid="item-count">{count} {count === 1 ? 'item' : 'items'}</span>
        </div>
      </div>
      <div className="basket-scroll" ref={scrollRef}>
        <table className="basket-table">
          <thead>
            <tr><th>Item</th><th className="num">Qty</th><th className="num">Each</th><th className="num">Total</th></tr>
          </thead>
          <tbody>
            {lines.map((line) => (
              <Fragment key={line.lineId}>
              <tr
                data-testid="basket-line"
                data-barcode={line.barcode}
                className={line.lineId === selectedLineId ? 'selected' : undefined}
                aria-selected={line.lineId === selectedLineId}
                onClick={() => onSelect(line.lineId)}
              >
                <td className="item">
                  <span data-testid="line-name">{line.name}</span>
                  {line.vatCode && <span className="vat-code" data-testid="line-vat-code" title={`VAT code ${line.vatCode}`}>{line.vatCode}</span>}
                  {line.pricedBy === 'WEIGHT' && line.weightGrams != null && (
                    <span className="line-detail" data-testid="line-weight">
                      {formatKg(line.weightGrams)} × {formatPence(line.unitPricePence)}/kg
                    </span>
                  )}
                  {line.pricedBy === 'LABEL' && <span className="line-detail" data-testid="line-label">Price-embedded label</span>}
                </td>
                <td className="num qty" data-testid="line-qty">{line.quantity}</td>
                <td className="num each" data-testid="line-each">
                  {formatPence(line.unitPricePence)}{line.pricedBy === 'WEIGHT' ? '/kg' : ''}
                </td>
                <td className="num total" data-testid="line-total">{formatPence(line.linePence)}</td>
              </tr>
              {savingsByLine.get(line.lineId)?.map((saving) => (
                <tr key={`${saving.code}-${saving.label}`} className="saving-line" data-testid="saving-line" data-code={saving.code}>
                  <td className="item" colSpan={3} data-testid="saving-label">{saving.label}</td>
                  <td className="num total" data-testid="saving-amount">-{formatPence(-saving.amountPence)}</td>
                </tr>
              ))}
              </Fragment>
            ))}
          </tbody>
        </table>
        {lines.length === 0 && <p className="empty" data-testid="basket-empty">Scan an item to start the sale</p>}
      </div>
      <div className="totals">
        <div className="row"><span>Subtotal</span><span data-testid="subtotal">{formatPence(basket?.subtotalPence ?? 0)}</span></div>
        <div className="row savings"><span>Savings</span><span data-testid="savings">{formatPence(basket?.savingsPence ?? 0)}</span></div>
        <div className="row grand"><span>Total to pay</span><span data-testid="total">{formatPence(basket?.totalPence ?? 0)}</span></div>
        <VatSummary bands={basket?.vatSummary ?? []} testId="basket-vat-summary" />
      </div>
    </section>
  )
}
