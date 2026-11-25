import { describe, expect, it } from 'vitest'
import { TillApiError } from '../api/client'
import type { Product, TillSession } from '../api/types'
import { OfflineStore, addLocalLine, completeOffline, isHealthy, isLocal, isUnreachable, newLocalBasket, setLocalQuantity, voidLocalLine } from './offline'

class MemoryStorage implements Storage {
  private data = new Map<string, string>()
  get length() { return this.data.size }
  clear() { this.data.clear() }
  getItem(key: string) { return this.data.get(key) ?? null }
  key(i: number) { return [...this.data.keys()][i] ?? null }
  removeItem(key: string) { this.data.delete(key) }
  setItem(key: string, value: string) { this.data.set(key, value) }
}

const session: TillSession = {
  storeNumber: '0417', storeName: 'Bradford Demo', tillId: 'till-1', operator: { id: 'op', name: 'Op' }, currency: 'GBP',
  transactionNumber: '0417-till-1-0001', basketId: 'b1', backendConnected: true,
  services: { 'product-service': 'UP', 'inventory-service': 'UP', 'order-service': 'UP', 'notification-service': 'UP' },
}
const milk: Product = { barcode: '1001', skuCode: 'MOR-MILK', name: 'Milk', pricePence: 155, quickKey: true }
const bread: Product = { barcode: '1002', skuCode: 'MOR-BREAD', name: 'Bread', pricePence: 120, quickKey: false }

describe('offline basket', () => {
  it('prices locally from the cached catalogue and merges repeat scans', () => {
    let basket = newLocalBasket(session, '0417-till-1-OFF1')
    expect(isLocal(basket)).toBe(true)
    basket = addLocalLine(addLocalLine(addLocalLine(basket, milk), bread), milk)
    expect(basket.lines).toHaveLength(2)
    expect(basket.itemCount).toBe(3)
    expect(basket.totalPence).toBe(155 * 2 + 120)
    const breadLine = basket.lines.find((l) => l.barcode === '1002')!
    basket = setLocalQuantity(basket, breadLine.lineId, 3)
    expect(basket.totalPence).toBe(155 * 2 + 360)
    basket = voidLocalLine(basket, breadLine.lineId)
    expect(basket.totalPence).toBe(310)
  })

  it('completes a cash sale into a queued sale with change and no order number', () => {
    const basket = addLocalLine(newLocalBasket(session, '0417-till-1-OFF1'), milk)
    const next = newLocalBasket(session, '0417-till-1-OFF2')
    const { sale, result } = completeOffline(basket, session, 500, next)
    expect(sale).toMatchObject({ tillId: 'till-1', tenderType: 'CASH', totalPence: 155, amountTenderedPence: 500 })
    expect('offline-' + sale.saleId).toBe(basket.basketId)
    expect(result).toMatchObject({ status: 'APPROVED', orderNumber: null, changeDuePence: 345, nextBasketId: next.basketId })
  })
})

describe('offline store', () => {
  it('keeps the queue per till, idempotent on sale id', () => {
    const storage = new MemoryStorage()
    const store = new OfflineStore('till-1', storage)
    const { sale } = completeOffline(addLocalLine(newLocalBasket(session, 'T'), milk), session, 155, newLocalBasket(session, 'T2'))
    store.enqueue(sale)
    store.enqueue(sale)
    expect(store.queue()).toHaveLength(1)
    expect(new OfflineStore('till-2', storage).queue()).toHaveLength(0)
    store.remove(sale.saleId)
    expect(store.queue()).toHaveLength(0)
  })

  it('caches the catalogue and keeps quick keys', () => {
    const store = new OfflineStore('till-1', new MemoryStorage())
    store.cacheProducts([milk])
    store.cacheProducts([{ ...milk, quickKey: false }, bread])
    expect(Object.keys(store.catalogue())).toEqual(['1001', '1002'])
    expect(store.quickKeys().map((p) => p.barcode)).toEqual(['1001'])
    expect(store.nextOfflineTransaction(session)).toBe('0417-till-1-OFF1')
    expect(store.nextOfflineTransaction(session)).toBe('0417-till-1-OFF2')
  })
})

describe('health', () => {
  it('is offline when a sale service is down or the backend is unreachable', () => {
    expect(isHealthy(session)).toBe(true)
    expect(isHealthy({ ...session, services: { ...session.services, 'notification-service': 'DOWN' } })).toBe(true)
    expect(isHealthy({ ...session, services: { ...session.services, 'order-service': 'DOWN' } })).toBe(false)
    expect(isUnreachable(new TypeError('Failed to fetch'))).toBe(true)
    expect(isUnreachable(new TillApiError(502, 'HTTP_502', 'Bad Gateway'))).toBe(true)
    expect(isUnreachable(new TillApiError(503, 'BACKEND_UNAVAILABLE', 'down'))).toBe(true)
    expect(isUnreachable(new TillApiError(404, 'UNKNOWN_BARCODE', 'nope'))).toBe(false)
  })
})

describe('isUnreachable', () => {
  it('treats network and gateway failures as offline', () => {
    expect(isUnreachable(new TypeError('Failed to fetch'))).toBe(true)
    expect(isUnreachable(new TillApiError(502, 'HTTP_502', 'Bad Gateway'))).toBe(true)
    expect(isUnreachable(new TillApiError(503, 'Service Unavailable', 'gateway: no till-service instance'))).toBe(true)
    expect(isUnreachable(new TillApiError(503, 'BACKEND_UNAVAILABLE', 'order-service is unavailable'))).toBe(true)
  })

  it('keeps the till online when the backend answers with its own error, even a 503', () => {
    expect(isUnreachable(new TillApiError(503, 'MORE_CARD_UNAVAILABLE', 'More Card is not available on this till'))).toBe(false)
    expect(isUnreachable(new TillApiError(422, 'UNKNOWN_BARCODE', 'No product'))).toBe(false)
    expect(isUnreachable(new TillApiError(409, 'OUT_OF_STOCK', 'Out of stock'))).toBe(false)
  })
})
