// MBA-3940 store-and-forward: the till keeps selling cash while the backend is unreachable, then replays the sales.
import { TillApiError } from '../api/client'
import type { Basket, BasketLine, Product, TenderResult, TillSession } from '../api/types'

export const LOCAL_BASKET_PREFIX = 'offline-'

export interface OfflineSale {
  saleId: string
  tillId: string
  transactionNumber: string
  tenderType: 'CASH'
  lines: { barcode: string; skuCode: string; name: string; quantity: number; unitPricePence: number }[]
  amountTenderedPence: number
  totalPence: number
  completedAt: string
}

export interface OfflineSaleResult {
  saleId: string
  orderNumber: string
  transactionNumber: string
  totalPence: number
  amountTenderedPence: number
}

/** Services a sale needs; the till is offline when the session check fails or any of these is down. */
const SALE_SERVICES = ['product-service', 'inventory-service', 'order-service']

export const isHealthy = (s: TillSession) => SALE_SERVICES.every((name) => s.services?.[name] !== 'DOWN')

/** A network failure, or the gateway / till-service saying a backend is unreachable. */
export const isUnreachable = (e: unknown) =>
  !(e instanceof TillApiError) || [502, 503, 504].includes(e.status) || e.code === 'BACKEND_UNAVAILABLE'

export const isLocal = (basket: Basket | null | undefined) => !!basket?.basketId.startsWith(LOCAL_BASKET_PREFIX)

const newId = () =>
  globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`

export class OfflineStore {
  private readonly tillKey: string
  private readonly storage: Storage

  constructor(tillKey: string, storage: Storage = window.localStorage) {
    this.tillKey = tillKey
    this.storage = storage
  }

  private read<T>(key: string, fallback: T): T {
    try {
      const raw = this.storage.getItem(key)
      return raw ? (JSON.parse(raw) as T) : fallback
    } catch {
      return fallback
    }
  }

  private write(key: string, value: unknown) {
    this.storage.setItem(key, JSON.stringify(value))
  }

  private get queueKey() {
    return `morrisons-till.offline-queue.${this.tillKey}`
  }

  catalogue(): Record<string, Product> {
    return this.read('morrisons-till.catalogue', {})
  }

  cacheProducts(products: Product[]) {
    const catalogue = this.catalogue()
    for (const p of products) catalogue[p.barcode] = { ...p, quickKey: p.quickKey || !!catalogue[p.barcode]?.quickKey }
    this.write('morrisons-till.catalogue', catalogue)
  }

  quickKeys(): Product[] {
    return Object.values(this.catalogue()).filter((p) => p.quickKey)
  }

  session(): TillSession | null {
    return this.read(`morrisons-till.session.${this.tillKey}`, null)
  }

  cacheSession(session: TillSession) {
    this.write(`morrisons-till.session.${this.tillKey}`, session)
  }

  queue(): OfflineSale[] {
    return this.read(this.queueKey, [])
  }

  enqueue(sale: OfflineSale) {
    this.write(this.queueKey, [...this.queue().filter((s) => s.saleId !== sale.saleId), sale])
  }

  remove(saleId: string) {
    this.write(this.queueKey, this.queue().filter((s) => s.saleId !== saleId))
  }

  nextOfflineTransaction(session: TillSession) {
    const key = `morrisons-till.offline-seq.${this.tillKey}`
    const seq = this.read(key, 0) + 1
    this.write(key, seq)
    return `${session.storeNumber}-${session.tillId}-OFF${seq}`
  }
}

export function reprice(basket: Basket): Basket {
  const lines = basket.lines.map((l) => ({ ...l, linePence: l.unitPricePence * l.quantity }))
  const subtotal = lines.reduce((sum, l) => sum + l.linePence, 0)
  return {
    ...basket,
    lines,
    adjustments: [],
    itemCount: lines.reduce((sum, l) => sum + l.quantity, 0),
    subtotalPence: subtotal,
    savingsPence: 0,
    totalPence: subtotal,
    vatSummary: [],
    tenders: [],
    paidPence: 0,
    balanceDuePence: subtotal,
  }
}

export function newLocalBasket(session: TillSession, transactionNumber: string, lines: BasketLine[] = []): Basket {
  return reprice({
    basketId: LOCAL_BASKET_PREFIX + newId(),
    tillId: session.tillId,
    transactionNumber,
    status: 'OPEN',
    currency: 'GBP',
    lines,
    adjustments: [],
    lastLineId: lines.at(-1)?.lineId ?? null,
    itemCount: 0,
    subtotalPence: 0,
    savingsPence: 0,
    totalPence: 0,
    vatSummary: [],
    orderNumber: null,
    member: null,
    tenders: [],
    paidPence: 0,
    balanceDuePence: 0,
  })
}

export function addLocalLine(basket: Basket, product: Product, quantity = 1): Basket {
  const existing = basket.lines.find((l) => l.barcode === product.barcode)
  if (existing) return setLocalQuantity(basket, existing.lineId, existing.quantity + quantity)
  const line: BasketLine = {
    lineId: newId(),
    barcode: product.barcode,
    skuCode: product.skuCode,
    name: product.name,
    quantity,
    unitPricePence: product.pricePence,
    linePence: 0,
    vatCode: product.vatCode ?? null,
    pricedBy: 'EACH',
    weightGrams: null,
  }
  return reprice({ ...basket, lines: [line, ...basket.lines], lastLineId: line.lineId })
}

export function setLocalQuantity(basket: Basket, lineId: string, quantity: number): Basket {
  if (quantity < 1) return voidLocalLine(basket, lineId)
  return reprice({ ...basket, lines: basket.lines.map((l) => (l.lineId === lineId ? { ...l, quantity } : l)), lastLineId: lineId })
}

export function voidLocalLine(basket: Basket, lineId: string): Basket {
  const lines = basket.lines.filter((l) => l.lineId !== lineId)
  return reprice({ ...basket, lines, lastLineId: lines[0]?.lineId ?? null })
}

/** Completes a cash sale locally: the sale to queue, and the result the till shows (no order number until synced). */
export function completeOffline(
  basket: Basket,
  session: TillSession,
  amountTenderedPence: number,
  nextBasket: Basket,
): { sale: OfflineSale; result: TenderResult } {
  const sale: OfflineSale = {
    saleId: basket.basketId.slice(LOCAL_BASKET_PREFIX.length),
    tillId: session.tillId,
    transactionNumber: basket.transactionNumber,
    tenderType: 'CASH',
    lines: basket.lines.map(({ barcode, skuCode, name, quantity, unitPricePence }) => ({ barcode, skuCode, name, quantity, unitPricePence })),
    amountTenderedPence,
    totalPence: basket.totalPence,
    completedAt: new Date().toISOString(),
  }
  const completed: Basket = { ...basket, status: 'COMPLETED' }
  const changeDuePence = amountTenderedPence - basket.totalPence
  return {
    sale,
    result: {
      status: 'APPROVED',
      tenderType: 'CASH',
      basket: completed,
      orderNumber: null,
      amountTenderedPence,
      changeDuePence,
      authCode: null,
      declineReason: null,
      receipt: {
        receiptNumber: 'R-' + basket.transactionNumber,
        storeNumber: session.storeNumber,
        storeName: session.storeName,
        tillId: session.tillId,
        operatorName: session.operator.name,
        transactionNumber: basket.transactionNumber,
        orderNumber: '',
        lines: basket.lines,
        subtotalPence: basket.subtotalPence,
        savingsPence: basket.savingsPence,
        totalPence: basket.totalPence,
        tenderType: 'CASH',
        amountTenderedPence,
        changeDuePence,
        authCode: null,
        issuedAt: sale.completedAt,
        vatSummary: [],
        moreCard: null,
        tenders: [{ tenderType: 'CASH', amountPence: basket.totalPence, tenderedPence: amountTenderedPence, changeDuePence, authCode: null }],
      },
      nextBasketId: nextBasket.basketId,
      balanceDuePence: 0,
      idempotencyKey: sale.saleId,
    },
  }
}
