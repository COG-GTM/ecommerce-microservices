import { useCallback, useEffect, useMemo, useState } from 'react'
import { TillApiError, tillApi } from './api/client'
import type { Basket, Product, TenderResult, TenderType, TillSession } from './api/types'
import { ApiChipsContext } from './components/ApiChip'
import { BasketPanel } from './components/BasketPanel'
import { Header } from './components/Header'
import { SaleComplete } from './components/SaleComplete'
import { SellPanel } from './components/SellPanel'
import { TenderPanel } from './components/TenderPanel'

type Screen = 'sell' | 'tender' | 'complete'

const params = new URLSearchParams(window.location.search)
const SHOW_API = params.get('api') === '1'
/** ?till=<id> picks the till; E2E tests use a unique till per test so they never share a basket. */
const TILL_ID = params.get('till') ?? undefined

const errorText = (e: unknown) => (e instanceof TillApiError ? e.message : 'Backend unavailable')

export default function App() {
  const [session, setSession] = useState<TillSession | null>(null)
  const [connected, setConnected] = useState(false)
  const [basket, setBasket] = useState<Basket | null>(null)
  const [quickKeys, setQuickKeys] = useState<Product[]>([])
  const [selectedLineId, setSelectedLineId] = useState<string | null>(null)
  const [screen, setScreen] = useState<Screen>('sell')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)
  const [declineMessage, setDeclineMessage] = useState<string | null>(null)
  const [tenderError, setTenderError] = useState<string | null>(null)
  const [result, setResult] = useState<TenderResult | null>(null)

  const showBasket = useCallback((next: Basket) => {
    setBasket(next)
    setSelectedLineId(next.lastLineId)
  }, [])

  const loadSession = useCallback(async () => {
    try {
      const s = await tillApi.session(TILL_ID)
      setSession(s)
      setConnected(s.backendConnected)
      // Recover after a failed first load or a till-service restart (the open basket id changes).
      setBasket((current) => {
        if (current?.basketId !== s.basketId && current?.status !== 'COMPLETED') {
          tillApi.basket(s.basketId).then(showBasket).catch(() => setConnected(false))
          tillApi.quickKeys().then(setQuickKeys).catch(() => setConnected(false))
        }
        return current
      })
      return s
    } catch {
      setConnected(false)
      return null
    }
  }, [showBasket])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      const s = await loadSession()
      if (!s || cancelled) return
      const [b, keys] = await Promise.all([tillApi.basket(s.basketId), tillApi.quickKeys()])
      if (!cancelled) {
        showBasket(b)
        setQuickKeys(keys)
      }
    })().catch(() => setConnected(false))
    const timer = window.setInterval(loadSession, 15000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [loadSession, showBasket])

  const run = async (action: () => Promise<void>, onError: (text: string) => void) => {
    setBusy(true)
    try {
      await action()
    } catch (e) {
      onError(errorText(e))
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
      const next = await tillApi.addLine(basket.basketId, barcode)
      showBasket(next)
      const line = next.lines.find((l) => l.lineId === next.lastLineId)
      setMessage({ kind: 'ok', text: `Added ${line?.name ?? barcode}` })
    }, (text) => setMessage({ kind: 'error', text }))

  const changeQty = (delta: number) =>
    basket && selectedLine &&
    run(async () => showBasket(await tillApi.updateLine(basket.basketId, selectedLine.lineId, selectedLine.quantity + delta)),
      (text) => setMessage({ kind: 'error', text }))

  const voidLine = () =>
    basket && selectedLine &&
    run(async () => {
      showBasket(await tillApi.voidLine(basket.basketId, selectedLine.lineId))
      setMessage({ kind: 'ok', text: `Voided ${selectedLine.name}` })
    }, (text) => setMessage({ kind: 'error', text }))

  const voidSale = () =>
    basket &&
    run(async () => {
      showBasket(await tillApi.voidSale(basket.basketId))
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

  const payByCard = (type: TenderType, outcome: 'APPROVED' | 'DECLINED') =>
    basket &&
    run(async () => {
      setDeclineMessage(null)
      setTenderError(null)
      applyTender(await tillApi.tender(basket.basketId, type, { terminalOutcome: outcome, expectedTotalPence: basket.totalPence }))
    }, setTenderError)

  const payByCash = (amountTenderedPence: number) =>
    basket &&
    run(async () => {
      setTenderError(null)
      applyTender(await tillApi.tender(basket.basketId, 'CASH', { amountTenderedPence, expectedTotalPence: basket.totalPence }))
    }, setTenderError)

  const newSale = () =>
    result &&
    run(async () => {
      showBasket(await tillApi.basket(result.nextBasketId))
      setResult(null)
      setMessage(null)
      setScreen('sell')
      await loadSession()
    }, (text) => setMessage({ kind: 'error', text }))

  const resetTenderState = () => {
    setDeclineMessage(null)
    setTenderError(null)
  }

  return (
    <ApiChipsContext.Provider value={SHOW_API}>
      <div className="till" data-screen={screen}>
        <Header session={session} connected={connected} transactionNumber={basket?.transactionNumber} />
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
