import type { AgeDecision, Basket, Product, TenderResult, TenderType, TillSession } from './types'

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
  const body = text ? JSON.parse(text) : null
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
  addLine: (basketId: string, barcode: string, quantity = 1) =>
    request<Basket>('/basket/lines', json('POST', { basketId, barcode, quantity })),
  updateLine: (basketId: string, lineId: string, quantity: number) =>
    request<Basket>('/basket/lines/' + lineId, json('PATCH', { basketId, quantity })),
  voidLine: (basketId: string, lineId: string) =>
    request<Basket>('/basket/lines/' + lineId + '?basketId=' + basketId, { method: 'DELETE' }),
  ageCheck: (basketId: string, lineId: string, decision: AgeDecision, operatorId?: string) =>
    request<Basket>('/basket/lines/' + lineId + '/age-check', json('POST', { basketId, decision, operatorId })),
  voidSale: (basketId: string) => request<Basket>('/basket/void', json('POST', { basketId })),
  tender: (basketId: string, tenderType: TenderType, options: { amountTenderedPence?: number; terminalOutcome?: 'APPROVED' | 'DECLINED'; expectedTotalPence?: number } = {}) =>
    request<TenderResult>('/basket/tender', json('POST', { basketId, tenderType, ...options })),
}
