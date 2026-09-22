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

export interface OrderRequest {
  storeId: string;
  registerId: string;
  associateId: string;
  lineItems: Array<{ skuCode: string; quantity: number }>;
  promotions: string[];
  tenders: Tender[];
  taxExempt: boolean;
}

export interface OrderResponse {
  orderNumber: string;
  status: string;
  message: string;
}
