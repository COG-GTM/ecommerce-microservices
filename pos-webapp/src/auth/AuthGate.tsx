import type { ReactNode } from 'react';
import { hasPosRole } from './identity';
import { useAuth } from './useAuth';
import styles from './AuthGate.module.css';

interface AuthGateProps {
  children: ReactNode;
}

export function AuthGate({ children }: AuthGateProps) {
  const { status, identity, login, logout, error } = useAuth();

  if (status === 'loading') {
    return <main className={styles.page}>Connecting to sign-in…</main>;
  }

  if (status === 'error') {
    return (
      <main className={styles.page}>
        <section className={styles.card}>
          <Brand />
          <p className={styles.message}>Unable to connect to sign-in.</p>
          {error && <p className={styles.detail}>{error}</p>}
          <button className={styles.primaryButton} onClick={() => window.location.reload()} type="button">
            Retry
          </button>
        </section>
      </main>
    );
  }

  if (status === 'unauthenticated') {
    return (
      <main className={styles.page}>
        <section className={styles.card}>
          <Brand />
          <p className={styles.message}>Sign in to open this register</p>
          <button className={styles.primaryButton} onClick={() => void login?.()} type="button">
            Sign in
          </button>
        </section>
      </main>
    );
  }

  if (!identity || !hasPosRole(identity)) {
    return (
      <main className={styles.page}>
        <section className={styles.card}>
          <Brand />
          <p className={styles.message}>This account is not authorized for store checkout</p>
          <button className={styles.secondaryButton} onClick={() => void logout?.()} type="button">
            Sign out
          </button>
        </section>
      </main>
    );
  }

  return children;
}

function Brand() {
  return (
    <>
      <div className={styles.logo}>Gap</div>
      <h1 className={styles.title}>Store Checkout</h1>
    </>
  );
}
