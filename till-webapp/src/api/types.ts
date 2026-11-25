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
  minimumAge?: number | null
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
  pendingAgeChecks?: number
  tenders: TenderLine[]
  paidPence: number
  balanceDuePence: number
}

export type TenderType = 'CARD' | 'CONTACTLESS' | 'CASH'
/** Simulated PIN pad result; TIMEOUT models a charge whose response never reached the till. */
export type TerminalOutcome = 'APPROVED' | 'DECLINED' | 'TIMEOUT'

export interface TenderLine {
  tenderType: TenderType
  amountPence: number
  tenderedPence: number
  changeDuePence: number
  authCode: string | null
}

export interface CashQuote {
  totalPence: number
  paidPence: number
  balanceDuePence: number
  amountTenderedPence: number
  changeDuePence: number
  remainingPence: number
}

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
  tenderType: TenderType | 'SPLIT'
  amountTenderedPence: number
  changeDuePence: number
  authCode: string | null
  issuedAt: string
  vatSummary: VatBand[]
  moreCard: MoreCardReceipt | null
  tenders: TenderLine[]
}

export interface MoreCardReceipt {
  cardLast4: string
  memberName: string
  pointsEarned: number
  pointsBalance: number
}

export interface TenderResult {
  status: 'APPROVED' | 'DECLINED' | 'PARTIAL' | 'TIMEOUT'
  tenderType: TenderType
  basket: Basket
  orderNumber: string | null
  amountTenderedPence: number
  changeDuePence: number
  authCode: string | null
  declineReason: string | null
  receipt: Receipt | null
  nextBasketId: string
  balanceDuePence: number
  /** Key of this tender attempt; resend it to retry after a terminal timeout. */
  idempotencyKey: string
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
