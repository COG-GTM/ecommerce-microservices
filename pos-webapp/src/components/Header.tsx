import styles from './Header.module.css';

export interface HeaderProps {
  storeId: string;
  registerId: string;
  associateName: string;
  associateId: string;
  transactionId: string;
  mode: 'Mock data' | 'Live gateway';
  onLogout?: () => void | Promise<void>;
}

export function Header({
  storeId,
  registerId,
  associateName,
  associateId,
  transactionId,
  mode,
  onLogout,
}: HeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.brand}>
        <div className={styles.logo}>Gap</div>
        <div className={styles.titleBlock}>
          <h1 className={styles.title}>Store Checkout</h1>
          <span className={styles.meta}>
            Store {storeId} · Register {registerId}
          </span>
        </div>
      </div>

      <div className={styles.right}>
        <div className={styles.field}>
          <span className={styles.fieldLabel}>Associate</span>
          <span className={styles.fieldValue}>
            {associateName} · {associateId}
          </span>
        </div>
        <div className={styles.field}>
          <span className={styles.fieldLabel}>Transaction</span>
          <span className={styles.fieldValue}>{transactionId}</span>
        </div>
        <div className={styles.status}>
          <span className={styles.dot} />
          {mode}
        </div>
        {onLogout && (
          <button className={styles.logoutButton} onClick={onLogout} type="button">
            Sign out
          </button>
        )}
      </div>
    </header>
  );
}
