import { useState } from 'react'
import type { TenderResult } from '../api/types'
import { formatPence } from '../money'
import { ApiChip } from './ApiChip'
import { VatSummary } from './VatSummary'

const PAID_BY: Record<string, string> = { CARD: 'card', CONTACTLESS: 'contactless', CASH: 'cash' }

export function SaleComplete({ result, onNewSale }: { result: TenderResult; onNewSale: () => void }) {
  const [showReceipt, setShowReceipt] = useState(false)
  const receipt = result.receipt
  return (
    <section className="panel complete" data-testid="sale-complete">
      <div className="tick" aria-hidden="true">
        <svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
      </div>
      <h2>Sale complete</h2>
      <p className="muted paid">Paid by {PAID_BY[result.tenderType]} · {formatPence(receipt?.totalPence ?? 0)}</p>
      {result.tenderType === 'CASH' && (
        <div className="give-change" data-testid="give-change">
          <div>Give change</div>
          <div className="give-change-amount">{formatPence(result.changeDuePence)}</div>
        </div>
      )}
      <p className="order-ref">Order <span data-testid="order-number">{result.orderNumber}</span></p>
      <ApiChip>POST /api/till/basket/tender → receipt</ApiChip>
      <div className="row-buttons">
        <button type="button" className="btn primary big" data-testid="start-new-sale" onClick={onNewSale}>Start new sale</button>
        <button type="button" className="btn outline big" data-testid="reprint-receipt" onClick={() => setShowReceipt((v) => !v)}>
          {showReceipt ? 'Hide receipt' : 'Reprint receipt'}
        </button>
      </div>
      {showReceipt && receipt && (
        <div className="receipt" data-testid="receipt">
          <div className="receipt-head">
            <strong>Store {receipt.storeNumber} {receipt.storeName}</strong>
            <span>Till {receipt.tillId} · {receipt.operatorName}</span>
            <span>Txn {receipt.transactionNumber}</span>
          </div>
          {receipt.lines.map((l) => (
            <div className="receipt-row" key={l.lineId}><span>{l.quantity} × {l.name}{l.vatCode && <span className="vat-code">{l.vatCode}</span>}</span><span>{formatPence(l.linePence)}</span></div>
          ))}
          <div className="receipt-row rule"><span>Subtotal</span><span>{formatPence(receipt.subtotalPence)}</span></div>
          <div className="receipt-row"><span>Savings</span><span>{formatPence(receipt.savingsPence)}</span></div>
          <div className="receipt-row strong"><span>Total</span><span data-testid="receipt-total">{formatPence(receipt.totalPence)}</span></div>
          <div className="receipt-row"><span className="cap">{PAID_BY[receipt.tenderType]}</span><span>{formatPence(receipt.amountTenderedPence)}</span></div>
          {receipt.changeDuePence > 0 && <div className="receipt-row"><span>Change</span><span>{formatPence(receipt.changeDuePence)}</span></div>}
          {receipt.authCode && <div className="receipt-row"><span>Auth code</span><span>{receipt.authCode}</span></div>}
          <VatSummary bands={receipt.vatSummary ?? []} testId="receipt-vat-summary" />
          <div className="receipt-foot">{receipt.receiptNumber} · {new Date(receipt.issuedAt).toLocaleString('en-GB')}</div>
        </div>
      )}
    </section>
  )
}
