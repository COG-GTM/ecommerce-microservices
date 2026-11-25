import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { TillApiError, tillApi } from './api/client'
import type { AgeDecision, Basket, Product, TenderResult, TenderType, TerminalOutcome, TillSession } from './api/types'
import { AgeCheckPrompt } from './components/AgeCheckPrompt'
import { ApiChipsContext } from './components/ApiChip'
import { BasketPanel } from './components/BasketPanel'
import { Header } from './components/Header'
import { RefundPanel } from './components/RefundPanel'
import { SaleComplete } from './components/SaleComplete'
import { SellPanel } from './components/SellPanel'
import { TenderPanel } from './components/TenderPanel'
import { WeightDialog } from './components/WeightDialog'
import { formatKg, formatPence } from './money'
import {
  OfflineStore,
  addLocalLine,
  completeOffline,
  isHealthy,
  isLocal,
  isUnreachable,
  newLocalBasket,
  setLocalQuantity,
  voidLocalLine,
} from './offline/offline'

type Screen = 'sell' | 'tender' | 'complete' | 'refund'

const params = new URLSearchParams(window.location.search)
const SHOW_API = params.get('api') === '1'
/** ?till=<id> picks the till; E2E tests use a unique till per test so they never share a basket. */
const TILL_ID = params.get('till') ?? undefined

/** More Card numbers start 9826; the scanner routes them to the loyalty endpoint instead of a product lookup. */
const isMoreCard = (code: string) => /^9826\d{8,15}$/.test(code.replace(/\s/g, ''))

const errorText = (e: unknown) => (e instanceof TillApiError ? e.message : 'Backend unavailable')
const HEALTH_CHECK_MS = 5000
const offlineStore = new OfflineStore(TILL_ID ?? 'default')

const newAttemptKey = () =>
  typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`

/** The card attempt in progress for this till; kept in sessionStorage so a reload can still retry it with the same key. */
interface CardAttempt {
  basketId: string
  key: string
  /** The terminal timed out or the response was lost: the basket stays locked until this key is retried. */
  unresolved: boolean
}
const ATTEMPT_STORAGE_KEY = `till.cardAttempt.${TILL_ID ?? 'default'}`
const loadAttempt = (): CardAttempt | null => {
  try {
    return JSON.parse(sessionStorage.getItem(ATTEMPT_STORAGE_KEY) ?? 'null')
  } catch {
    return null
  }
}
const saveAttempt = (attempt: CardAttempt | null) => {
  if (attempt) sessionStorage.setItem(ATTEMPT_STORAGE_KEY, JSON.stringify(attempt))
  else sessionStorage.removeItem(ATTEMPT_STORAGE_KEY)
}

const DECLINED_MESSAGE = 'Card declined. Ask for another payment method.'
const TIMEOUT_MESSAGE = 'No response from the PIN pad. Retry the payment; the customer will not be charged twice.'

export default function App() {
  const [session, setSession] = useState<TillSession | null>(null)
  const [connected, setConnected] = useState(false)
  const [healthKnown, setHealthKnown] = useState(false)
  const [basket, setBasket] = useState<Basket | null>(null)
  const [quickKeys, setQuickKeys] = useState<Product[]>([])
  const [selectedLineId, setSelectedLineId] = useState<string | null>(null)
  const [screen, setScreen] = useState<Screen>('sell')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)
  const [declineMessage, setDeclineMessage] = useState<string | null>(null)
  const [tenderError, setTenderError] = useState<string | null>(null)
  const [cardAttempt, setCardAttemptState] = useState<CardAttempt | null>(loadAttempt)
  const setCardAttempt = (attempt: CardAttempt | null) => {
    saveAttempt(attempt)
    setCardAttemptState(attempt)
  }
  const paymentUnresolved = !!cardAttempt?.unresolved && cardAttempt.basketId === basket?.basketId
  const [result, setResult] = useState<TenderResult | null>(null)
  const [weighing, setWeighing] = useState<Product | null>(null)
  const [ageCheckLineId, setAgeCheckLineId] = useState<string | null>(null)
  const [queued, setQueued] = useState(() => offlineStore.queue().length)
  const offline = healthKnown && !connected
  const basketRef = useRef<Basket | null>(null)
  basketRef.current = basket
  const syncingRef = useRef(false)
  const reconnectingRef = useRef(false)
  const [weightError, setWeightError] = useState<string | null>(null)

  const showBasket = useCallback((next: Basket) => {
    setBasket(next)
    setSelectedLineId(next.lastLineId)
  }, [])

  const markOffline = useCallback(() => {
    setConnected(false)
    setHealthKnown(true)
  }, [])

  const showQuickKeys = useCallback((keys: Product[]) => {
    offlineStore.cacheProducts(keys)
    setQuickKeys(keys)
  }, [])

  /** Replays queued offline sales, oldest first. Each is idempotent on its sale id, so a retry never duplicates. */
  const flushQueue = useCallback(async () => {
    if (syncingRef.current || offlineStore.queue().length === 0) return
    syncingRef.current = true
    let synced = 0
    try {
      for (const sale of offlineStore.queue()) {
        try {
          await tillApi.syncOfflineSale(sale)
          offlineStore.remove(sale.saleId)
          synced++
        } catch (e) {
          if (isUnreachable(e)) break
          setMessage({ kind: 'error', text: `Offline sale ${sale.transactionNumber} not synced: ${errorText(e)}` })
        }
      }
    } finally {
      syncingRef.current = false
      setQueued(offlineStore.queue().length)
      if (synced > 0) setMessage({ kind: 'ok', text: `Synced ${synced} offline sale${synced === 1 ? '' : 's'}` })
    }
  }, [])

  /** Back online with nothing in the offline basket: return to the till's server basket. */
  const returnToServerBasket = useCallback(async (s: TillSession) => {
    if (reconnectingRef.current) return
    reconnectingRef.current = true
    try {
      const next = offlineStore.abandonedBasket() === s.basketId ? await tillApi.voidSale(s.basketId) : await tillApi.basket(s.basketId)
      offlineStore.setAbandonedBasket(null)
      // Keep anything scanned into the local basket while the request was in flight; it is sold offline instead.
      const current = basketRef.current
      if (!isLocal(current) || current?.lines.length === 0) showBasket(next)
      showQuickKeys(await tillApi.quickKeys())
    } catch (e) {
      if (isUnreachable(e)) markOffline()
    } finally {
      reconnectingRef.current = false
    }
  }, [markOffline, showBasket, showQuickKeys])

  const loadSession = useCallback(async () => {
    try {
      const s = await tillApi.session(TILL_ID)
      const healthy = isHealthy(s)
      setSession(s)
      offlineStore.cacheSession(s)
      setConnected(healthy)
      setHealthKnown(true)
      if (!healthy) return s
      void flushQueue()
      const current = basketRef.current
      // Recover after a failed first load, a till-service restart (the open basket id changes) or a reconnect.
      if (current?.status !== 'COMPLETED' && current?.basketId !== s.basketId && (!isLocal(current) || current?.lines.length === 0)) {
        await returnToServerBasket(s)
      }
      return s
    } catch {
      markOffline()
      setSession((current) => current ?? offlineStore.session())
      return null
    }
  }, [flushQueue, markOffline, returnToServerBasket])

  useEffect(() => {
    void loadSession()
    const timer = window.setInterval(loadSession, HEALTH_CHECK_MS)
    window.addEventListener('online', loadSession)
    window.addEventListener('offline', markOffline)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('online', loadSession)
      window.removeEventListener('offline', markOffline)
    }
  }, [loadSession, markOffline])

  const newOfflineBasket = useCallback(
    (s: TillSession, lines: Basket['lines'] = []) => newLocalBasket(s, offlineStore.nextOfflineTransaction(s), lines),
    [],
  )

  // Going offline: keep selling from the cached catalogue in a local basket, carrying over any lines already scanned.
  // Also re-runs when a server response lands after the till went offline, so a late server basket is converted too.
  useEffect(() => {
    if (!offline || !session) return
    setQuickKeys((keys) => (keys.length ? keys : offlineStore.quickKeys()))
    if (basket?.status === 'COMPLETED' || isLocal(basket)) return
    if (basket) offlineStore.setAbandonedBasket(basket.basketId)
    showBasket(newOfflineBasket(session, basket?.lines ?? []))
  }, [offline, session, basket, newOfflineBasket, showBasket])

  const run = async (action: () => Promise<void>, onError: (text: string) => void) => {
    setBusy(true)
    try {
      await action()
    } catch (e) {
      if (isUnreachable(e)) {
        markOffline()
        onError('Backend unreachable. The till is now offline; try again.')
      } else {
        onError(errorText(e))
      }
    } finally {
      setBusy(false)
    }
  }

  const selectedLine = useMemo(
    () => basket?.lines.find((l) => l.lineId === selectedLineId) ?? null,
    [basket, selectedLineId],
  )

  const ageCheckLine = useMemo(
    () => basket?.lines.find((l) => l.lineId === ageCheckLineId && l.ageCheck === 'PENDING') ?? null,
    [basket, ageCheckLineId],
  )
  const pendingAgeLines = basket?.lines.filter((l) => l.ageCheck === 'PENDING') ?? []

  const scan = (barcode: string) =>
    basket &&
    run(async () => {
      if (isLocal(basket)) {
        const product = offlineStore.catalogue()[barcode]
        if (!product) throw new TillApiError(404, 'NOT_CACHED', `Item ${barcode} is not available offline`)
        if (product.soldByWeight || product.minimumAge) throw new TillApiError(422, 'ONLINE_ONLY', `${product.name} needs the till online (weighed or age-restricted)`)
        showBasket(addLocalLine(basket, product))
        setMessage({ kind: 'ok', text: `Added ${product.name}` })
        return
      }
      if (isMoreCard(barcode)) {
        const next = await tillApi.attachMember(basket.basketId, barcode)
        setBasket(next)
        setMessage({ kind: 'ok', text: `More Card: ${next.member?.name ?? 'member'} added` })
        return
      }
      try {
        const next = await tillApi.addLine(basket.basketId, barcode)
        showBasket(next)
        const line = next.lines.find((l) => l.lineId === next.lastLineId)
        if (line && line.pricedBy === 'EACH' && !line.minimumAge) {
          const cached = offlineStore.catalogue()[line.barcode]
          offlineStore.cacheProducts([{ barcode: line.barcode, skuCode: line.skuCode, name: line.name, pricePence: line.unitPricePence, quickKey: !!cached?.quickKey, vatCode: line.vatCode ?? cached?.vatCode ?? 'S' }])
        }
        setMessage({ kind: 'ok', text: `Added ${line?.name ?? barcode}` })
        if (line?.ageCheck === 'PENDING') setAgeCheckLineId(line.lineId)
      } catch (e) {
        if (!(e instanceof TillApiError && e.code === 'WEIGHT_REQUIRED')) throw e
        setWeighing(await tillApi.product(barcode))
        setMessage({ kind: 'ok', text: e.message })
      }
    }, (text) => setMessage({ kind: 'error', text }))

  const closeWeighing = () => {
    setWeighing(null)
    setWeightError(null)
    requestAnimationFrame(() => document.querySelector<HTMLInputElement>('[data-testid="barcode-input"]')?.focus())
  }

  // A pending weight belongs to the sale it was keyed for; drop it if the basket is replaced (void elsewhere, restart).
  useEffect(() => {
    setWeighing(null)
    setWeightError(null)
  }, [basket?.basketId])

  const addWeighed = (weightGrams: number) =>
    basket && weighing &&
    run(async () => {
      const next = await tillApi.addLine(basket.basketId, weighing.barcode, 1, weightGrams)
      showBasket(next)
      closeWeighing()
      setMessage({ kind: 'ok', text: `Added ${weighing.name} ${formatKg(weightGrams)}` })
    }, setWeightError)

  const decideAge = (decision: AgeDecision) =>
    basket && ageCheckLine &&
    run(async () => {
      showBasket(await tillApi.ageCheck(basket.basketId, ageCheckLine.lineId, decision))
      setAgeCheckLineId(null)
      setMessage(decision === 'APPROVED'
        ? { kind: 'ok', text: `ID checked for ${ageCheckLine.name}` }
        : { kind: 'ok', text: `Refused ${ageCheckLine.name}: removed from the sale` })
    }, (text) => setMessage({ kind: 'error', text }))

  const selectLine = (lineId: string) => {
    if (screen !== 'sell') return
    setSelectedLineId(lineId)
    if (basket?.lines.some((l) => l.lineId === lineId && l.ageCheck === 'PENDING')) setAgeCheckLineId(lineId)
  }

  const changeQty = (delta: number) =>
    basket && selectedLine &&
    run(async () => showBasket(isLocal(basket)
      ? setLocalQuantity(basket, selectedLine.lineId, selectedLine.quantity + delta)
      : await tillApi.updateLine(basket.basketId, selectedLine.lineId, selectedLine.quantity + delta)),
    (text) => setMessage({ kind: 'error', text }))

  const voidLine = () =>
    basket && selectedLine &&
    run(async () => {
      showBasket(isLocal(basket) ? voidLocalLine(basket, selectedLine.lineId) : await tillApi.voidLine(basket.basketId, selectedLine.lineId))
      setMessage({ kind: 'ok', text: `Voided ${selectedLine.name}` })
    }, (text) => setMessage({ kind: 'error', text }))

  const voidSale = () =>
    basket &&
    run(async () => {
      if (isLocal(basket) && session) {
        showBasket(newOfflineBasket(session))
        setMessage({ kind: 'ok', text: 'Sale voided' })
      } else {
        const { paidPence } = await tillApi.basket(basket.basketId)
        showBasket(await tillApi.voidSale(basket.basketId))
        setMessage({ kind: 'ok', text: paidPence > 0 ? `Sale voided. Return ${formatPence(paidPence)} cash` : 'Sale voided' })
      }
      setScreen('sell')
    }, (text) => setMessage({ kind: 'error', text }))

  const applyTender = (r: TenderResult) => {
    if (r.status === 'APPROVED') {
      setResult(r)
      setBasket(r.basket)
      setScreen('complete')
    } else if (r.status === 'PARTIAL') {
      showBasket(r.basket)
    } else {
      showBasket(r.basket)
      setDeclineMessage(r.status === 'TIMEOUT' ? TIMEOUT_MESSAGE : DECLINED_MESSAGE)
    }
  }

  const tenderFailed = (text: string) => {
    setTenderError(text)
    if (basket && !isLocal(basket)) tillApi.basket(basket.basketId).then(setBasket, () => undefined)
  }

  const cardAvailable = !offline && !isLocal(basket)

  const payByCard = (type: TenderType, outcome: TerminalOutcome) =>
    basket && cardAvailable &&
    run(async () => {
      setDeclineMessage(null)
      setTenderError(null)
      // Reuse the key until the attempt settles, so a retry after a timeout or a lost response is never a second charge.
      const attempt = cardAttempt?.basketId === basket.basketId ? cardAttempt : { basketId: basket.basketId, key: newAttemptKey(), unresolved: false }
      setCardAttempt(attempt)
      let r: TenderResult
      try {
        r = await tillApi.tender(basket.basketId, type, { terminalOutcome: outcome, expectedTotalPence: basket.totalPence, idempotencyKey: attempt.key, expectedBalanceDuePence: basket.balanceDuePence })
      } catch (e) {
        if (!(e instanceof TillApiError)) {
          setCardAttempt({ ...attempt, unresolved: true })
          setDeclineMessage(TIMEOUT_MESSAGE)
          return
        }
        throw e
      }
      setCardAttempt(r.status === 'TIMEOUT' ? { ...attempt, unresolved: true } : null)
      applyTender(r)
    }, tenderFailed)

  const payByCash = (amountTenderedPence: number, partialPayment = false) =>
    basket &&
    run(async () => {
      setDeclineMessage(null)
      setTenderError(null)
      if (isLocal(basket) && session) {
        const { sale, result: r } = completeOffline(basket, session, amountTenderedPence, newOfflineBasket(session))
        offlineStore.enqueue(sale)
        setQueued(offlineStore.queue().length)
        applyTender(r)
        if (!offline) void flushQueue()
        return
      }
      applyTender(await tillApi.tender(basket.basketId, 'CASH', { amountTenderedPence, partialPayment, expectedTotalPence: basket.totalPence, expectedBalanceDuePence: basket.balanceDuePence }))
    }, tenderFailed)

  const newSale = () =>
    result &&
    run(async () => {
      setResult(null)
      setMessage(null)
      setScreen('sell')
      if (!isLocal(result.basket)) {
        try {
          showBasket(await tillApi.basket(result.nextBasketId))
        } catch (e) {
          if (!isUnreachable(e) || !session) throw e
          markOffline()
          showBasket(newOfflineBasket(session))
          return
        }
        await loadSession()
      } else if (session) {
        showBasket(newOfflineBasket(session))
        if (!offline) await loadSession()
      }
    }, (text) => setMessage({ kind: 'error', text }))

  const resetTenderState = () => {
    setDeclineMessage(null)
    setTenderError(null)
  }

  return (
    <ApiChipsContext.Provider value={SHOW_API}>
      <div className="till" data-screen={screen}>
        <Header session={session} connected={connected} healthKnown={healthKnown} queued={queued} transactionNumber={basket?.transactionNumber} member={basket?.member ?? null} />
        <main className="till-main">
          <BasketPanel basket={basket} selectedLineId={selectedLineId} onSelect={selectLine} />
          <div className={'work work-' + screen}>
            {screen === 'sell' && (
              <SellPanel
                basket={basket}
                quickKeys={quickKeys}
                selectedLine={selectedLine}
                message={message}
                busy={busy || !basket}
                onScan={scan}
                onQty={changeQty}
                onVoidLine={voidLine}
                onVoidSale={voidSale}
                onPay={() => {
                  if (pendingAgeLines.length > 0) {
                    setMessage({ kind: 'error', text: 'Approve or refuse age-restricted items before taking payment' })
                    setAgeCheckLineId(pendingAgeLines[0].lineId)
                    return
                  }
                  resetTenderState()
                  setScreen('tender')
                }}
                onRefund={() => setScreen('refund')}
              />
            )}
            {screen === 'tender' && basket && (
              <TenderPanel
                basketId={basket.basketId}
                totalPence={basket.totalPence}
                paidPence={basket.paidPence ?? 0}
                balanceDuePence={basket.balanceDuePence ?? basket.totalPence}
                busy={busy}
                cardAvailable={cardAvailable}
                declineMessage={declineMessage}
                paymentUnresolved={paymentUnresolved}
                errorMessage={tenderError}
                onBack={() => setScreen('sell')}
                onCard={payByCard}
                onCash={payByCash}
                onMethodChange={resetTenderState}
                onVoidSale={voidSale}
              />
            )}
            {screen === 'complete' && result && <SaleComplete result={result} onNewSale={newSale} />}
            {screen === 'sell' && weighing && (
              <WeightDialog product={weighing} busy={busy} error={weightError} onConfirm={addWeighed} onCancel={() => {
                closeWeighing()
                setMessage(null)
              }} />
            )}
            {screen === 'refund' && <RefundPanel tillId={TILL_ID} onExit={() => setScreen('sell')} />}
          </div>
          {screen === 'sell' && ageCheckLine && (
            <AgeCheckPrompt
              line={ageCheckLine}
              busy={busy}
              onDecide={decideAge}
              onLater={() => setAgeCheckLineId(null)}
            />
          )}
        </main>
      </div>
    </ApiChipsContext.Provider>
  )
}
