import { useEffect, useState } from 'react'
import { tillApi } from '../api/client'
import type { CashQuote, TenderType, TerminalOutcome } from '../api/types'
import { formatPence } from '../money'
import { ApiChip } from './ApiChip'
import { Keypad } from './Keypad'

const METHODS: { type: TenderType; title: string; sub: string }[] = [
  { type: 'CARD', title: 'Card', sub: 'Chip and PIN' },
  { type: 'CONTACTLESS', title: 'Contactless', sub: 'Tap on PIN pad' },
  { type: 'CASH', title: 'Cash', sub: 'Notes and coins' },
]
const NOTES = [500, 1000, 2000, 5000]

interface Props {
  basketId: string
  totalPence: number
  paidPence: number
  balanceDuePence: number
  busy: boolean
  /** False while offline: only cash can be taken. */
  cardAvailable: boolean
  declineMessage: string | null
  /** The last card attempt timed out: only a retry of that attempt is allowed until it resolves. */
  paymentUnresolved: boolean
  errorMessage: string | null
  onBack: () => void
  onCard: (type: TenderType, outcome: TerminalOutcome) => void
  onCash: (amountTenderedPence: number, partialPayment?: boolean) => void
  onMethodChange: () => void
  onVoidSale: () => void
}

export function TenderPanel(props: Props) {
  const { basketId, totalPence, paidPence, balanceDuePence, busy, paymentUnresolved, cardAvailable } = props
  // A timed-out card attempt (also after a reload) opens straight on the PIN pad so the operator can retry it.
  const [chosen, setMethod] = useState<TenderType | null>(paymentUnresolved ? 'CARD' : null)
  const method = chosen === 'CASH' || cardAvailable ? chosen : null
  const [cashPence, setCashPence] = useState(0)
  const [quote, setQuote] = useState<CashQuote | null>(null)

  useEffect(() => {
    setCashPence(0)
  }, [paidPence])

  useEffect(() => {
    if (method !== 'CASH' || cashPence === 0) {
      setQuote(null)
      return
    }
    let current = true
    tillApi.cashQuote(basketId, cashPence).then(
      (q) => current && setQuote(q),
      () => current && setQuote(null),
    )
    return () => {
      current = false
    }
  }, [method, basketId, cashPence, paidPence])

  const choose = (type: TenderType) => {
    setMethod(type)
    setCashPence(0)
    props.onMethodChange()
  }
  const onCashKey = (key: string) =>
    setCashPence((value) => (key === 'C' ? 0 : Math.min(Number(String(value) + key), 99_999_99)))
  const liveQuote = quote && quote.amountTenderedPence === cashPence && quote.paidPence === paidPence ? quote : null
  const covers = cashPence >= balanceDuePence

  return (
    <>
      <section className="panel due" aria-label="Amount due">
        <div>
          <div className="label">{paidPence > 0 ? 'Left to pay' : 'Amount due'}</div>
          <div className="due-amount" data-testid="amount-due">{formatPence(balanceDuePence)}</div>
          {paidPence > 0 && (
            <div className="muted paid-so-far" data-testid="paid-so-far">
              Paid {formatPence(paidPence)} of {formatPence(totalPence)}
            </div>
          )}
        </div>
        <div className="panel-head-right">
          <ApiChip>POST /api/till/basket/tender</ApiChip>
          {method === 'CASH' && <ApiChip>GET /api/till/basket/:id/cash-quote</ApiChip>}
          <button type="button" className="btn outline back" data-testid="back-to-basket" disabled={busy || paidPence > 0 || paymentUnresolved} onClick={props.onBack}>
            Back to basket
          </button>
          {paidPence > 0 && (
            <button type="button" className="btn danger" data-testid="void-part-paid-sale" disabled={busy} onClick={props.onVoidSale}>
              Void sale
            </button>
          )}
        </div>
      </section>

      <div className="methods" role="radiogroup" aria-label="Payment method">
        {METHODS.map((m) => (
          <button
            key={m.type}
            type="button"
            role="radio"
            aria-checked={method === m.type}
            className={'method' + (method === m.type ? ' active' : '')}
            data-testid={`tender-${m.type.toLowerCase()}`}
            disabled={busy || paymentUnresolved || (m.type !== 'CASH' && !cardAvailable)}
            onClick={() => choose(m.type)}
          >
            <span className="method-title">{m.title}</span>
            <span className="method-sub">{m.sub}</span>
          </button>
        ))}
      </div>

      {!cardAvailable && (
        <p className="panel offline-note" data-testid="card-offline-note" role="status">
          Offline: card payments are unavailable. Take cash; the sale syncs when the till reconnects.
        </p>
      )}

      {method === null && (
        <section className="panel dashed choose" data-testid="choose-method">Choose how the customer is paying</section>
      )}

      {(method === 'CARD' || method === 'CONTACTLESS') && (
        <section className="panel pinpad" data-testid="pinpad-wait">
          <svg className="pinpad-icon" viewBox="0 0 24 36" aria-hidden="true">
            <rect x="2" y="2" width="20" height="32" rx="3" fill="none" stroke="currentColor" strokeWidth="2.2" />
            <rect x="6" y="7" width="12" height="2" rx="1" fill="currentColor" />
            {[13, 18, 23].map((y) => (
              <g key={y}><circle cx="8" cy={y} r="1.3" fill="currentColor" /><rect x="11" y={y - 1} width="7" height="2" rx="1" fill="currentColor" /></g>
            ))}
          </svg>
          <h3>{method === 'CARD' ? 'Waiting for customer on PIN pad' : 'Waiting for customer to tap on PIN pad'}</h3>
          <p className="muted">The payment terminal result comes back from the backend</p>
          <div className="row-buttons">
            {paymentUnresolved ? (
              <button type="button" className="btn primary" data-testid="retry-payment" disabled={busy} onClick={() => props.onCard(method, 'APPROVED')}>Retry payment</button>
            ) : (
              <>
                <button type="button" className="btn primary" data-testid="simulate-approved" disabled={busy} onClick={() => props.onCard(method, 'APPROVED')}>Simulate approved</button>
                <button type="button" className="btn danger" data-testid="simulate-declined" disabled={busy} onClick={() => props.onCard(method, 'DECLINED')}>Simulate declined</button>
                <button type="button" className="btn outline" data-testid="simulate-timeout" disabled={busy} onClick={() => props.onCard(method, 'TIMEOUT')}>Simulate timeout</button>
              </>
            )}
          </div>
          {busy && <p className="muted" data-testid="terminal-waiting" role="status">Contacting PIN pad…</p>}
          {props.declineMessage && <p className="decline" data-testid="decline-message" role="alert">{props.declineMessage}</p>}
          {props.errorMessage && <p className="decline" data-testid="tender-error" role="alert">{props.errorMessage}</p>}
        </section>
      )}

      {method === 'CASH' && (
        <div className="cash">
          <section className="panel cash-main">
            <div className="label">Cash received</div>
            <div className="cash-received" data-testid="cash-received">{formatPence(cashPence)}</div>
            <div className="notes">
              {NOTES.map((n) => (
                <button key={n} type="button" className="btn outline note" data-testid={`cash-${n / 100}`} onClick={() => setCashPence((value) => value + n)}>
                  £{n / 100}
                </button>
              ))}
              <button type="button" className="btn outline note" data-testid="cash-exact" onClick={() => setCashPence(balanceDuePence)}>Exact</button>
            </div>
            <div className="change-row">
              <span>Change due</span>
              <span className="change" data-testid="change-due">
                {covers && liveQuote ? formatPence(liveQuote.changeDuePence) : '—'}
              </span>
            </div>
            {cashPence > 0 && !covers && (
              <div className="change-row remaining-row">
                <span>Left to pay by card</span>
                <span className="remaining" data-testid="remaining-after-cash">
                  {liveQuote ? formatPence(liveQuote.remainingPence) : '—'}
                </span>
              </div>
            )}
            {props.errorMessage && <p className="decline" data-testid="tender-error" role="alert">{props.errorMessage}</p>}
            <button type="button" className="pay drawer" data-testid="take-cash" disabled={busy || !covers} onClick={() => props.onCash(cashPence)}>
              Take cash and open drawer
            </button>
            {cashPence > 0 && !covers && (
              <button type="button" className="btn outline part-cash" data-testid="take-part-cash" disabled={busy} onClick={() => props.onCash(cashPence, true)}>
                Take {formatPence(cashPence)} cash, pay the rest by card
              </button>
            )}
          </section>
          <section className="panel keypad-panel">
            <Keypad id="cash-key" onKey={onCashKey} />
          </section>
        </div>
      )}
    </>
  )
}
