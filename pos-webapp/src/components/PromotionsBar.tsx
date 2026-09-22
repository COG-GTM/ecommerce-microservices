import { useState, type FormEvent } from 'react';
import type { Promotion } from '../api/types';
import styles from './PromotionsBar.module.css';

export interface PromotionsBarProps {
  promotions: Promotion[];
  onApply: (code: string) => void;
  onRemove: (code: string) => void;
}

export function PromotionsBar({ promotions, onApply, onRemove }: PromotionsBarProps) {
  const [code, setCode] = useState('');

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return;
    onApply(trimmed);
    setCode('');
  }

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
                onClick={() => onRemove(promo.code)}
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
          value={code}
          onChange={(event) => setCode(event.target.value)}
        />
        <button className={styles.button} type="submit">
          Apply
        </button>
      </form>
    </div>
  );
}
