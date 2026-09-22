import { useState, type FormEvent } from 'react';
import styles from './ItemLookup.module.css';

export interface ItemLookupProps {
  onLookup: (skuCode: string) => Promise<boolean>;
}

export function ItemLookup({ onLookup }: ItemLookupProps) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const skuCode = value.trim();
    if (!skuCode) return;

    setBusy(true);
    const found = await onLookup(skuCode);
    setBusy(false);

    if (found) {
      setValue('');
      setError(null);
    } else {
      setError(`No item found for ${skuCode}`);
    }
  }

  return (
    <form className={styles.lookup} onSubmit={handleSubmit}>
      <label className={styles.label} htmlFor="sku-lookup">
        Look up — scan hangtag (style-color-size)
      </label>
      <div className={styles.row}>
        <input
          id="sku-lookup"
          className={styles.input}
          placeholder="268341-016-L"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          autoComplete="off"
        />
        <button className={styles.button} type="submit" disabled={busy}>
          {busy ? 'Looking up' : 'Add to bag'}
        </button>
        <button
          className={`${styles.button} ${styles.secondary}`}
          type="button"
          onClick={() => {
            setValue('');
            setError(null);
          }}
        >
          Clear
        </button>
      </div>
      {error ? (
        <p className={styles.error}>{error}</p>
      ) : (
        <p className={styles.hint}>Keyed entry accepted when the hangtag will not scan.</p>
      )}
    </form>
  );
}
