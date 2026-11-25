import type { AgeDecision, BasketLine } from '../api/types'
import { ApiChip } from './ApiChip'

interface Props {
  line: BasketLine
  busy: boolean
  onDecide: (decision: AgeDecision) => void
  onLater: () => void
}

/** Challenge 25: the operator must confirm photo ID was seen, or refuse the item, before the sale can be paid. */
export function AgeCheckPrompt({ line, busy, onDecide, onLater }: Props) {
  return (
    <div className="age-overlay">
      <section className="age-prompt" role="dialog" aria-modal="true" aria-labelledby="age-title" data-testid="age-check-prompt">
        <div className="age-head">
          <span className="age-logo" aria-hidden="true">25</span>
          <div>
            <h2 id="age-title">Challenge 25</h2>
            <p className="muted" data-testid="age-check-item">{line.name} · must be {line.minimumAge} or over</p>
          </div>
        </div>
        <p className="age-copy">If the customer looks under 25, ask for photo ID (passport, driving licence or PASS card).</p>
        <div className="age-actions">
          <button type="button" className="btn primary big" data-testid="age-approve" disabled={busy} onClick={() => onDecide('APPROVED')}>
            ID seen · approve
          </button>
          <button type="button" className="btn danger big" data-testid="age-refuse" disabled={busy} onClick={() => onDecide('REFUSED')}>
            Refuse item
          </button>
        </div>
        <div className="age-foot">
          <button type="button" className="btn outline" data-testid="age-later" disabled={busy} onClick={onLater}>Decide later</button>
          <ApiChip>{'POST /api/till/basket/lines/{lineId}/age-check'}</ApiChip>
        </div>
      </section>
    </div>
  )
}
