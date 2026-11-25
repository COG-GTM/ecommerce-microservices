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
  /** Price per kg when soldByWeight. */
  pricePence: number
  quickKey: boolean
  vatCode: VatCode
  soldByWeight?: boolean
}

/** UK VAT code: Z = zero-rated (0%), S = standard-rated (20%). */
export type VatCode = 'Z' | 'S'

/** VAT for one rate band, computed server-side once per band (not per line). grossPence = netPence + vatPence. */
export interface VatBand {
  code: VatCode
  ratePercent: number
  netPence: number
  vatPence: number
  grossPence: number
}

export interface BasketLine {
  lineId: string
  barcode: string
  skuCode: string
  name: string
  quantity: number
  /** Per kg for WEIGHT lines; the label price for LABEL lines. */
  unitPricePence: number
  linePence: number
  vatCode: VatCode | null
  pricedBy: 'EACH' | 'WEIGHT' | 'LABEL'
  weightGrams: number | null
}

export interface Adjustment {
  code: string
  label: string
  amountPence: number
  lineId: string | null
}

export interface MoreCardMember {
  cardLast4: string
  name: string
  pointsBalance: number
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
  vatSummary: VatBand[]
  orderNumber: string | null
  member: MoreCardMember | null
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
  vatSummary: VatBand[]
  moreCard: MoreCardReceipt | null
}

export interface MoreCardReceipt {
  cardLast4: string
  memberName: string
  pointsEarned: number
  pointsBalance: number
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
