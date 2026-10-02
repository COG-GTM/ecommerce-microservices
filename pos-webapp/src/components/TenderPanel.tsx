import type { TenderType } from '../api/types';
import { TENDERS, parseAmount, type TenderLine } from '../lib/tenders';
import { formatCurrency, roundCurrency } from '../lib/totals';
import styles from './TenderPanel.module.css';

export interface TenderPanelProps {
  amountDue: number;
  selected: TenderType;
  splitTender: boolean;
  splitLines: TenderLine[];
  cashReceived: string;
  status: string | null;
  error: string | null;
  changeDue: number | null;
  disabled: boolean;
  onSelect: (tender: TenderType) => void;
  onSplitTenderChange: (split: boolean) => void;
  onCashReceivedChange: (amount: string) => void;
  onSplitLinesChange: (lines: TenderLine[]) => void;
  onCharge: () => void;
}

export function TenderPanel({
  amountDue,
  selected,
  splitTender,
  splitLines,
  cashReceived,
  status,
  error,
  changeDue,
  disabled,
  onSelect,
  onSplitTenderChange,
  onCashReceivedChange,
  onSplitLinesChange,
  onCharge,
}: TenderPanelProps) {
  const tendered = splitTender
    ? roundCurrency(splitLines.reduce((sum, line) => sum + parseAmount(line.amount), 0))
    : selected === 'CASH'
      ? parseAmount(cashReceived)
      : amountDue;
  const remaining = roundCurrency(Math.max(amountDue - tendered, 0));
  const expectedChange = roundCurrency(Math.max(tendered - amountDue, 0));

  function updateLine(index: number, patch: Partial<TenderLine>) {
    onSplitLinesChange(
      splitLines.map((line, i) => (i === index ? { ...line, ...patch } : line)),
    );
  }

  return (
    <section className={styles.panel} aria-label="Tender">
      <h2 className={styles.heading}>Tender</h2>
      <div className={styles.body}>
        {!splitTender &&
          TENDERS.map((tender) => (
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

        {!splitTender && selected === 'CASH' && (
          <label className={styles.amountRow} htmlFor="cash-received">
            Cash received
            <input
              id="cash-received"
              className={styles.amountInput}
              inputMode="decimal"
              value={cashReceived}
              onChange={(event) => onCashReceivedChange(event.target.value)}
            />
          </label>
        )}

        {splitTender && (
          <div className={styles.splitLines} aria-label="Split tenders">
            {splitLines.map((line, index) => (
              <div className={styles.splitLine} key={index}>
                <select
                  aria-label={`Tender ${index + 1} type`}
                  className={styles.typeSelect}
                  value={line.type}
                  onChange={(event) =>
                    updateLine(index, { type: event.target.value as TenderType })
                  }
                >
                  {TENDERS.map((tender) => (
                    <option key={tender.type} value={tender.type}>
                      {tender.label}
                    </option>
                  ))}
                </select>
                <input
                  aria-label={`Tender ${index + 1} amount`}
                  className={styles.amountInput}
                  inputMode="decimal"
                  value={line.amount}
                  onChange={(event) => updateLine(index, { amount: event.target.value })}
                />
                <button
                  type="button"
                  className={styles.removeLine}
                  disabled={splitLines.length <= 1}
                  onClick={() => onSplitLinesChange(splitLines.filter((_, i) => i !== index))}
                >
                  Remove
                </button>
              </div>
            ))}
            <button
              type="button"
              className={styles.addLine}
              onClick={() =>
                onSplitLinesChange([
                  ...splitLines,
                  { type: 'CASH', amount: remaining > 0 ? remaining.toFixed(2) : '' },
                ])
              }
            >
              Add tender
            </button>
          </div>
        )}

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

        {(splitTender || selected === 'CASH') && changeDue === null && (
          <div className={styles.splitRow}>
            <span>Remaining {formatCurrency(remaining)}</span>
            <span>Change due {formatCurrency(expectedChange)}</span>
          </div>
        )}

        <button className={styles.charge} type="button" onClick={onCharge} disabled={disabled}>
          Charge {formatCurrency(amountDue)}
        </button>
        {status && <p className={styles.status}>{status}</p>}
        {changeDue !== null && changeDue > 0 && (
          <p className={styles.change}>Change due {formatCurrency(changeDue)}</p>
        )}
        {error && (
          <p className={styles.error} role="alert">
            {error}
          </p>
        )}
      </div>
    </section>
  );
}
