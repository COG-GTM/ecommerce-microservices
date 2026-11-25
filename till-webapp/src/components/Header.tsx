import type { TillSession } from '../api/types'
import { ApiChip } from './ApiChip'

interface Props {
  session: TillSession | null
  connected: boolean
  transactionNumber: string | undefined
}

export function Header({ session, connected, transactionNumber }: Props) {
  const txn = transactionNumber?.split('-').pop()
  return (
    <header className="till-header">
      <div className="brand">
        <span className="letter-mark" aria-hidden="true">M</span>
        <div>
          <div className="brand-title" data-testid="store-till">
            Store {session ? `${session.storeNumber} ${session.storeName}` : '…'} · Till {session?.tillId ?? '…'}
          </div>
          <div className="brand-sub" data-testid="operator">Operator: {session?.operator.name ?? '…'}</div>
        </div>
      </div>
      <div className="header-right">
        <ApiChip>GET /api/till/session</ApiChip>
        <span className={'backend ' + (connected ? 'up' : 'down')} data-testid="backend-status">
          <span className="dot" /> {connected ? 'Backend connected' : 'Backend offline'}
        </span>
        <span className="txn" data-testid="transaction-number" title={transactionNumber}>
          Transaction #{txn ?? '----'}
        </span>
      </div>
    </header>
  )
}
