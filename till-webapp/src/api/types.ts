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
  minimumAge?: number | null
}

export interface BasketLine {
  lineId: string
  barcode: string
  skuCode: string
  name: string
  quantity: number
  unitPricePence: number
  linePence: number
  /** Set on age-restricted lines; payment is blocked while any line is PENDING. */
  minimumAge?: number | null
  ageCheck?: AgeCheck | null
  ageApprovedBy?: string | null
}

export type AgeCheck = 'PENDING' | 'APPROVED'
export type AgeDecision = 'APPROVED' | 'REFUSED'

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
  pendingAgeChecks?: number
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

export interface ApiError {
  error: string
  message: string
}
