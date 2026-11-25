import type { MoreCardMember, TillSession } from '../api/types'
import { ApiChip } from './ApiChip'

interface Props {
  session: TillSession | null
  connected: boolean
  healthKnown: boolean
  /** Offline sales waiting to sync. */
  queued: number
  transactionNumber: string | undefined
  member: MoreCardMember | null
}

export function Header({ session, connected, healthKnown, queued, transactionNumber, member }: Props) {
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
        {member && (
          <span className="more-card" data-testid="more-card-member" title="More Card member">
            More Card · {member.name} ····{member.cardLast4}
          </span>
        )}
        <span className={'backend ' + (connected ? 'up' : 'down')} data-testid="backend-status">
          <span className="dot" /> {connected ? 'Backend connected' : healthKnown ? 'Offline' : 'Connecting…'}
        </span>
        {queued > 0 && (
          <span className="offline-queue" data-testid="offline-queue" title="Sales saved offline, synced on reconnect">
            {queued} queued
          </span>
        )}
        <span className="txn" data-testid="transaction-number" title={transactionNumber}>
          Transaction #{txn ?? '----'}
        </span>
      </div>
    </header>
  )
}
