import type { TenderType } from '../api/types';
import { formatCurrency } from '../lib/totals';
import styles from './TenderPanel.module.css';

export interface TenderPanelProps {
  amountDue: number;
  selected: TenderType;
  splitTender: boolean;
  status: string | null;
  disabled: boolean;
  onSelect: (tender: TenderType) => void;
  onSplitTenderChange: (split: boolean) => void;
  onCharge: () => void;
}

const TENDERS: Array<{ type: TenderType; label: string; hint: string }> = [
  { type: 'CREDIT_DEBIT', label: 'Credit / debit', hint: 'Insert, tap or swipe' },
  { type: 'GIFT_CARD', label: 'Gift card', hint: 'Scan or key card number' },
  { type: 'MOBILE_WALLET', label: 'Mobile wallet', hint: 'Apple Pay, Google Pay' },
];

export function TenderPanel({
  amountDue,
  selected,
  splitTender,
  status,
  disabled,
  onSelect,
  onSplitTenderChange,
  onCharge,
}: TenderPanelProps) {
  return (
    <section className={styles.panel} aria-label="Tender">
      <h2 className={styles.heading}>Tender</h2>
      <div className={styles.body}>
        {TENDERS.map((tender) => (
          <button
            key={tender.type}
            type="button"
            className={`${styles.option} ${selected === tender.type ? styles.selected : ''}`}
            aria-pressed={selected === tender.type}
            onClick={() => onSelect(tender.type)}
          >
            <span className={styles.optionLabel}>{tender.label}</span>
            <span className={styles.optionHint}>{tender.hint}</span>
          </button>
        ))}

        <div className={styles.splitRow}>
          <label className={styles.split} htmlFor="split-tender">
            <input
              id="split-tender"
              type="checkbox"
              checked={splitTender}
              onChange={(event) => onSplitTenderChange(event.target.checked)}
            />
            Split tender
          </label>
          <span>Amount due {formatCurrency(amountDue)}</span>
        </div>

        <button className={styles.charge} type="button" onClick={onCharge} disabled={disabled}>
          Charge {formatCurrency(amountDue)}
        </button>
        {status && <p className={styles.status}>{status}</p>}
      </div>
    </section>
  );
}
