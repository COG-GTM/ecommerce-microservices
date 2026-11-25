import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { TillApiError, tillApi } from './api/client'
import type { Basket, Product, TenderResult, TenderType, TillSession } from './api/types'
import { ApiChipsContext } from './components/ApiChip'
import { BasketPanel } from './components/BasketPanel'
import { Header } from './components/Header'
import { SaleComplete } from './components/SaleComplete'
import { SellPanel } from './components/SellPanel'
import { TenderPanel } from './components/TenderPanel'
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

type Screen = 'sell' | 'tender' | 'complete'

const params = new URLSearchParams(window.location.search)
const SHOW_API = params.get('api') === '1'
/** ?till=<id> picks the till; E2E tests use a unique till per test so they never share a basket. */
const TILL_ID = params.get('till') ?? undefined

const errorText = (e: unknown) => (e instanceof TillApiError ? e.message : 'Backend unavailable')
const HEALTH_CHECK_MS = 5000
const offlineStore = new OfflineStore(TILL_ID ?? 'default')

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
  const [result, setResult] = useState<TenderResult | null>(null)
  const [queued, setQueued] = useState(() => offlineStore.queue().length)
  const offline = healthKnown && !connected
  const basketRef = useRef<Basket | null>(null)
  basketRef.current = basket
  const syncingRef = useRef(false)
  const reconnectingRef = useRef(false)

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

  const scan = (barcode: string) =>
    basket &&
    run(async () => {
      if (isLocal(basket)) {
        const product = offlineStore.catalogue()[barcode]
        if (!product) throw new TillApiError(404, 'NOT_CACHED', `Item ${barcode} is not available offline`)
        showBasket(addLocalLine(basket, product))
        setMessage({ kind: 'ok', text: `Added ${product.name}` })
        return
      }
      const next = await tillApi.addLine(basket.basketId, barcode)
      showBasket(next)
      const line = next.lines.find((l) => l.lineId === next.lastLineId)
      if (line) {
        const cached = offlineStore.catalogue()[line.barcode]
        offlineStore.cacheProducts([{ barcode: line.barcode, skuCode: line.skuCode, name: line.name, pricePence: line.unitPricePence, quickKey: !!cached?.quickKey }])
      }
      setMessage({ kind: 'ok', text: `Added ${line?.name ?? barcode}` })
    }, (text) => setMessage({ kind: 'error', text }))

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
      showBasket(isLocal(basket) && session ? newOfflineBasket(session) : await tillApi.voidSale(basket.basketId))
      setMessage({ kind: 'ok', text: 'Sale voided' })
    }, (text) => setMessage({ kind: 'error', text }))

  const applyTender = (r: TenderResult) => {
    if (r.status === 'APPROVED') {
      setResult(r)
      setBasket(r.basket)
      setScreen('complete')
    } else {
      showBasket(r.basket)
      setDeclineMessage('Card declined. Ask for another payment method.')
    }
  }

  const cardAvailable = !offline && !isLocal(basket)

  const payByCard = (type: TenderType, outcome: 'APPROVED' | 'DECLINED') =>
    basket && cardAvailable &&
    run(async () => {
      setDeclineMessage(null)
      setTenderError(null)
      applyTender(await tillApi.tender(basket.basketId, type, { terminalOutcome: outcome, expectedTotalPence: basket.totalPence }))
    }, setTenderError)

  const payByCash = (amountTenderedPence: number) =>
    basket &&
    run(async () => {
      setTenderError(null)
      if (isLocal(basket) && session) {
        const { sale, result: r } = completeOffline(basket, session, amountTenderedPence, newOfflineBasket(session))
        offlineStore.enqueue(sale)
        setQueued(offlineStore.queue().length)
        applyTender(r)
        if (!offline) void flushQueue()
        return
      }
      applyTender(await tillApi.tender(basket.basketId, 'CASH', { amountTenderedPence, expectedTotalPence: basket.totalPence }))
    }, setTenderError)

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
        <Header session={session} connected={connected} healthKnown={healthKnown} queued={queued} transactionNumber={basket?.transactionNumber} />
        <main className="till-main">
          <BasketPanel basket={basket} selectedLineId={selectedLineId} onSelect={(id) => screen === 'sell' && setSelectedLineId(id)} />
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
                  resetTenderState()
                  setScreen('tender')
                }}
              />
            )}
            {screen === 'tender' && basket && (
              <TenderPanel
                totalPence={basket.totalPence}
                busy={busy}
                cardAvailable={cardAvailable}
                declineMessage={declineMessage}
                errorMessage={tenderError}
                onBack={() => setScreen('sell')}
                onCard={payByCard}
                onCash={payByCash}
                onMethodChange={resetTenderState}
              />
            )}
            {screen === 'complete' && result && <SaleComplete result={result} onNewSale={newSale} />}
          </div>
        </main>
      </div>
    </ApiChipsContext.Provider>
  )
}
