import type { OfflineSale, OfflineSaleResult } from '../offline/offline'
import type { AgeDecision, Basket, CashQuote, Product, RefundableSale, RefundResult, TenderResult, TenderType, TerminalOutcome, TillSession } from './types'

const BASE = '/api/till'

export class TillApiError extends Error {
  readonly status: number
  readonly code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(BASE + path, {
    ...init,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...init?.headers },
  })
  const text = await response.text()
  let body = null
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    // e.g. an HTML 502 page from the proxy when the gateway is down
  }
  if (!response.ok) {
    throw new TillApiError(response.status, body?.error ?? 'HTTP_' + response.status, body?.message ?? response.statusText)
  }
  return body as T
}

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) })

export const tillApi = {
  session: (tillId?: string) => request<TillSession>('/session' + (tillId ? '?tillId=' + encodeURIComponent(tillId) : '')),
  quickKeys: () => request<Product[]>('/products?quickkey=true'),
  product: (barcode: string) => request<Product>('/products/' + encodeURIComponent(barcode)),
  basket: (basketId: string) => request<Basket>('/basket/' + basketId),
  addLine: (basketId: string, barcode: string, quantity = 1, weightGrams?: number) =>
    request<Basket>('/basket/lines', json('POST', { basketId, barcode, quantity, weightGrams })),
  updateLine: (basketId: string, lineId: string, quantity: number) =>
    request<Basket>('/basket/lines/' + lineId, json('PATCH', { basketId, quantity })),
  voidLine: (basketId: string, lineId: string) =>
    request<Basket>('/basket/lines/' + lineId + '?basketId=' + basketId, { method: 'DELETE' }),
  attachMember: (basketId: string, cardNumber: string) =>
    request<Basket>('/basket/member', json('POST', { basketId, cardNumber })),
  ageCheck: (basketId: string, lineId: string, decision: AgeDecision) =>
    request<Basket>('/basket/lines/' + lineId + '/age-check', json('POST', { basketId, decision })),
  voidSale: (basketId: string) => request<Basket>('/basket/void', json('POST', { basketId })),
  cashQuote: (basketId: string, amountTenderedPence: number) =>
    request<CashQuote>(`/basket/${encodeURIComponent(basketId)}/cash-quote?amountTenderedPence=${amountTenderedPence}`),
  tender: (basketId: string, tenderType: TenderType, options: { amountTenderedPence?: number; partialPayment?: boolean; expectedBalanceDuePence?: number; terminalOutcome?: TerminalOutcome; expectedTotalPence?: number; idempotencyKey?: string } = {}) =>
    request<TenderResult>('/basket/tender', json('POST', { basketId, tenderType, ...options })),
  refundLookup: (reference: string) => request<RefundableSale>('/refund/' + encodeURIComponent(reference)),
  refund: (reference: string, lines: { skuCode: string; quantity: number }[], tillId?: string) =>
    request<RefundResult>('/refund', json('POST', { reference, lines, tillId })),
  syncOfflineSale: (sale: OfflineSale) => request<OfflineSaleResult>('/offline-sales', json('POST', sale)),
}
