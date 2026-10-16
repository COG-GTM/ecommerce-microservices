export interface ProductVariant {
  skuCode: string;
  colorName: string;
  colorCode: string;
  size: string;
  listPrice: number;
  salePrice: number;
}

export interface Product {
  styleId: string;
  skuCode: string;
  name: string;
  description: string;
  department: string;
  category: string;
  colorName: string;
  colorCode: string;
  size: string;
  listPrice: number;
  salePrice: number;
  clearancePercent: number;
  finalSale: boolean;
  variants: ProductVariant[];
}

export interface NearbyStoreAvailability {
  storeId: string;
  storeName: string;
  distanceMiles: number;
  onHand: number;
}

export interface Inventory {
  skuCode: string;
  storeId: string;
  onHand: number;
  nearbyStores: NearbyStoreAvailability[];
  shipFromStoreEligible: boolean;
  floor: string;
  fixture: string;
}

export interface OrderLineItem {
  skuCode: string;
  styleId: string;
  description: string;
  department: string;
  colorName: string;
  colorCode: string;
  size: string;
  quantity: number;
  listPrice: number;
  unitPrice: number;
  extendedPrice: number;
  discountReason: string | null;
  clearancePercent: number;
  finalSale: boolean;
  inventory: Inventory;
}

export type TenderType = 'CREDIT_DEBIT' | 'GIFT_CARD' | 'MOBILE_WALLET';

export interface Tender {
  type: TenderType;
  label: string;
  amount: number;
}

export interface Promotion {
  code: string;
  description: string;
  percentOff: number;
  amountOff: number;
}

export interface OrderTotals {
  merchandiseTotal: number;
  servicesAndFees: number;
  discountTotal: number;
  taxableSubtotal: number;
  taxRate: number;
  salesTax: number;
  total: number;
  savedToday: number;
  taxExempt: boolean;
}

export interface Order {
  orderNumber: string;
  storeId: string;
  storeName: string;
  registerId: string;
  associateId: string;
  associateName: string;
  lineItems: OrderLineItem[];
  promotions: Promotion[];
  tenders: Tender[];
  totals: OrderTotals;
}

export type FulfillmentType = 'TAKE_WITH' | 'SHIP_FROM_STORE';

export interface ServiceFee {
  code: string;
  description: string;
  amount: number;
}

export type ReceiptChannel = 'EMAIL' | 'SMS' | 'PRINT' | 'NONE';

export interface ReceiptDelivery {
  channel: ReceiptChannel;
  /** Required for EMAIL and SMS. */
  destination?: string | null;
}

export interface OrderRequestLineItem {
  skuCode: string;
  quantity: number;
  /** Defaults to TAKE_WITH on the server. */
  fulfillmentType?: FulfillmentType;
  fulfillmentStoreId?: string | null;
}

export interface OrderRequest {
  storeId: string;
  registerId: string;
  associateId: string;
  lineItems: OrderRequestLineItem[];
  promotions: string[];
  tenders: Tender[];
  taxExempt: boolean;
  services: ServiceFee[];
  receiptDelivery?: ReceiptDelivery | null;
  /** Required when taxExempt is true. */
  taxExemptCertificate?: string | null;
}

export type OrderStatus = 'COMPLETED';

export interface AppliedPromotion {
  code: string;
  amount: number;
}

/** Server-priced line returned by order-service (additive to the frozen contract). */
export interface OrderResponseLine {
  skuCode: string;
  description: string;
  quantity: number;
  listPrice: number;
  unitPrice: number;
  markdownAmount: number;
  extendedPrice: number;
  finalSale: boolean;
  promotionAmount: number;
  promotions: AppliedPromotion[];
  fulfillmentType: FulfillmentType;
  fulfillmentStoreId: string | null;
}

export interface OrderResponse {
  orderNumber: string;
  status: OrderStatus;
  message: string;
  totals: OrderTotals;
  lineItems?: OrderResponseLine[];
  promotions?: AppliedPromotion[];
}

export type ApiProblemType =
  | 'sku-not-found'
  | 'out-of-stock'
  | 'invalid-promotion'
  | 'validation-failed'
  | 'dependency-unavailable';

/** RFC 7807 `application/problem+json` body (Spring 6 `ProblemDetail`). */
export interface ApiProblem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  [extension: string]: unknown;
}
