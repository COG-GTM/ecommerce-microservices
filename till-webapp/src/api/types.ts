// Shapes of the /api/till contract. Keep in sync with docs/morrisons-till/ARCHITECTURE.md.
export type ServiceStatus = 'UP' | 'DOWN'

export interface TillSession {
  storeNumber: string
  storeName: string
  tillId: string
  operator: { id: string; name: string }
  currency: 'GBP'
  transactionNumber: string
  basketId: string
  backendConnected: boolean
  services: Record<string, ServiceStatus>
}

export interface Product {
  barcode: string
  skuCode: string
  name: string
  pricePence: number
  quickKey: boolean
}

export interface BasketLine {
  lineId: string
  barcode: string
  skuCode: string
  name: string
  quantity: number
  unitPricePence: number
  linePence: number
}

export interface Adjustment {
  code: string
  label: string
  amountPence: number
  lineId: string | null
}

export interface Basket {
  basketId: string
  tillId: string
  transactionNumber: string
  status: 'OPEN' | 'COMPLETED' | 'VOIDED'
  currency: 'GBP'
  lines: BasketLine[]
  adjustments: Adjustment[]
  lastLineId: string | null
  itemCount: number
  subtotalPence: number
  savingsPence: number
  totalPence: number
  orderNumber: string | null
}

export type TenderType = 'CARD' | 'CONTACTLESS' | 'CASH'

export interface Receipt {
  receiptNumber: string
  storeNumber: string
  storeName: string
  tillId: string
  operatorName: string
  transactionNumber: string
  orderNumber: string
  lines: BasketLine[]
  subtotalPence: number
  savingsPence: number
  totalPence: number
  tenderType: TenderType
  amountTenderedPence: number
  changeDuePence: number
  authCode: string | null
  issuedAt: string
}

export interface TenderResult {
  status: 'APPROVED' | 'DECLINED'
  tenderType: TenderType
  basket: Basket
  orderNumber: string | null
  amountTenderedPence: number
  changeDuePence: number
  authCode: string | null
  declineReason: string | null
  receipt: Receipt | null
  nextBasketId: string
}

export interface RefundableLine {
  skuCode: string
  name: string
  unitPricePence: number
  soldQuantity: number
  refundedQuantity: number
  refundableQuantity: number
}

/** A completed sale found by receipt/order number, with what is still refundable against it. */
export interface RefundableSale {
  orderNumber: string
  /** null on orders written without till metadata */
  transactionId: string | null
  storeNumber: string | null
  tillId: string | null
  tenderType: TenderType | null
  totalPence: number
  refundedPence: number
  refundablePence: number
  lines: RefundableLine[]
  refunds: string[]
}

export interface RefundedLine {
  skuCode: string
  name: string
  quantity: number
  unitPricePence: number
  lineTotalPence: number
}

export interface RefundResult {
  status: 'REFUNDED'
  refundNumber: string
  originalOrderNumber: string
  transactionNumber: string
  tillId: string
  operatorName: string
  tenderType: TenderType | null
  refundPence: number
  lines: RefundedLine[]
  /** null if the refund committed but the refreshed sale could not be read */
  remaining: RefundableSale | null
  issuedAt: string
}

export interface ApiError {
  error: string
  message: string
}
