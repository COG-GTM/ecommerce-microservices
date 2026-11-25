import { useState } from 'react'
import type { TenderType } from '../api/types'
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
  totalPence: number
  busy: boolean
  /** False while offline: only cash can be taken. */
  cardAvailable: boolean
  declineMessage: string | null
  errorMessage: string | null
  onBack: () => void
  onCard: (type: TenderType, outcome: 'APPROVED' | 'DECLINED') => void
  onCash: (amountTenderedPence: number) => void
  onMethodChange: () => void
}

export function TenderPanel(props: Props) {
  const { totalPence, busy, cardAvailable } = props
  const [chosen, setMethod] = useState<TenderType | null>(null)
  const method = chosen === 'CASH' || cardAvailable ? chosen : null
  const [cashPence, setCashPence] = useState(0)

  const choose = (type: TenderType) => {
    setMethod(type)
    setCashPence(0)
    props.onMethodChange()
  }
  const onCashKey = (key: string) =>
    setCashPence((value) => (key === 'C' ? 0 : Math.min(Number(String(value) + key), 99_999_99)))
  const change = cashPence - totalPence

  return (
    <>
      <section className="panel due" aria-label="Amount due">
        <div>
          <div className="label">Amount due</div>
          <div className="due-amount" data-testid="amount-due">{formatPence(totalPence)}</div>
        </div>
        <div className="panel-head-right">
          <ApiChip>POST /api/till/basket/tender</ApiChip>
          <button type="button" className="btn outline back" data-testid="back-to-basket" disabled={busy} onClick={props.onBack}>
            Back to basket
          </button>
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
            disabled={busy || (m.type !== 'CASH' && !cardAvailable)}
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
            <button type="button" className="btn primary" data-testid="simulate-approved" disabled={busy} onClick={() => props.onCard(method, 'APPROVED')}>Simulate approved</button>
            <button type="button" className="btn danger" data-testid="simulate-declined" disabled={busy} onClick={() => props.onCard(method, 'DECLINED')}>Simulate declined</button>
          </div>
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
              <button type="button" className="btn outline note" data-testid="cash-exact" onClick={() => setCashPence(totalPence)}>Exact</button>
            </div>
            <div className="change-row">
              <span>Change due</span>
              <span className="change" data-testid="change-due">{cashPence >= totalPence ? formatPence(change) : '—'}</span>
            </div>
            {props.errorMessage && <p className="decline" data-testid="tender-error" role="alert">{props.errorMessage}</p>}
            <button type="button" className="pay drawer" data-testid="take-cash" disabled={busy || cashPence < totalPence} onClick={() => props.onCash(cashPence)}>
              Take cash and open drawer
            </button>
          </section>
          <section className="panel keypad-panel">
            <Keypad id="cash-key" onKey={onCashKey} />
          </section>
        </div>
      )}
    </>
  )
}
