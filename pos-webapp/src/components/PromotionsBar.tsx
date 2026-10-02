import { useState, type FormEvent } from 'react';
import type { Promotion } from '../api/types';
import styles from './PromotionsBar.module.css';

export type PromoApplyResult = 'applied' | 'unknown' | 'duplicate';

export interface PromotionsBarProps {
  promotions: Promotion[];
  onApply: (code: string) => PromoApplyResult;
  onRemove: (code: string) => void;
}

export function PromotionsBar({ promotions, onApply, onRemove }: PromotionsBarProps) {
  const [code, setCode] = useState('');
  const [feedback, setFeedback] = useState<{
    tone: 'success' | 'error';
    text: string;
  } | null>(null);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return;
    const result = onApply(trimmed);
    if (result === 'applied') {
      setFeedback({ tone: 'success', text: `${trimmed} applied.` });
      setCode('');
    } else if (result === 'duplicate') {
      setFeedback({ tone: 'error', text: `${trimmed} is already applied.` });
    } else {
      setFeedback({ tone: 'error', text: `${trimmed} is not a valid promo code.` });
    }
  }

  const feedbackClass =
    feedback?.tone === 'success'
      ? `${styles.feedback} ${styles.feedbackSuccess}`
      : `${styles.feedback} ${styles.feedbackError}`;

  return (
    <div className={styles.bar}>
      <div className={styles.left}>
        <span className={styles.label}>Promotions</span>
        {promotions.length === 0 ? (
          <span className={styles.label}>None applied</span>
        ) : (
          promotions.map((promo) => (
            <span className={styles.promo} key={promo.code}>
              {promo.code} — {promo.description}
              <button
                className={styles.remove}
                type="button"
                aria-label={`Remove ${promo.code}`}
                onClick={() => {
                  onRemove(promo.code);
                  setFeedback(null);
                }}
              >
                ×
              </button>
            </span>
          ))
        )}
      </div>

      <form className={styles.apply} onSubmit={handleSubmit}>
        <input
          className={styles.input}
          placeholder="Promo code"
          aria-label="Promo code"
          aria-invalid={feedback?.tone === 'error'}
          aria-describedby="promo-feedback"
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
            setFeedback(null);
          }}
        />
        <button className={styles.button} type="submit">
          Apply
        </button>
      </form>

      <p
        role="status"
        aria-live="polite"
        id="promo-feedback"
        className={feedback ? feedbackClass : styles.feedback}
      >
        {feedback?.text}
      </p>
    </div>
  );
}
