import { useEffect, useRef } from 'react'
import type { Basket } from '../api/types'
import { formatPence } from '../money'
import { ApiChip } from './ApiChip'

interface Props {
  basket: Basket | null
  selectedLineId: string | null
  onSelect: (lineId: string) => void
}

export function BasketPanel({ basket, selectedLineId, onSelect }: Props) {
  const lines = basket?.lines ?? []
  const count = basket?.itemCount ?? 0
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
              <tr
                key={line.lineId}
                data-testid="basket-line"
                data-barcode={line.barcode}
                className={line.lineId === selectedLineId ? 'selected' : undefined}
                aria-selected={line.lineId === selectedLineId}
                onClick={() => onSelect(line.lineId)}
              >
                <td className="item">
                  <span data-testid="line-name">{line.name}</span>
                  {line.ageCheck && (
                    <span className={'age-badge ' + line.ageCheck.toLowerCase()} data-testid="line-age">
                      {line.ageCheck === 'APPROVED' ? `ID checked · ${line.ageApprovedBy}` : `Challenge 25 · ${line.minimumAge}+`}
                    </span>
                  )}
                </td>
                <td className="num qty" data-testid="line-qty">{line.quantity}</td>
                <td className="num each" data-testid="line-each">{formatPence(line.unitPricePence)}</td>
                <td className="num total" data-testid="line-total">{formatPence(line.linePence)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {lines.length === 0 && <p className="empty" data-testid="basket-empty">Scan an item to start the sale</p>}
      </div>
      <div className="totals">
        <div className="row"><span>Subtotal</span><span data-testid="subtotal">{formatPence(basket?.subtotalPence ?? 0)}</span></div>
        <div className="row savings"><span>Savings</span><span data-testid="savings">{formatPence(basket?.savingsPence ?? 0)}</span></div>
        <div className="row grand"><span>Total to pay</span><span data-testid="total">{formatPence(basket?.totalPence ?? 0)}</span></div>
      </div>
    </section>
  )
}
