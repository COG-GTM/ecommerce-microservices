import type { Inventory, OrderLineItem, Product, Promotion } from './types';
import { STORE_ID } from './config';

export const MOCK_PRODUCTS: Product[] = [
  {
    styleId: '268341',
    skuCode: '268341-016-L',
    name: 'Vintage Soft Crewneck Tee',
    description: 'Vintage Soft Crewneck Tee',
    department: "WOMEN'S",
    category: 'TOPS',
    colorName: 'Heather Grey',
    colorCode: '#9b9ea3',
    size: 'L',
    listPrice: 29.95,
    salePrice: 17.97,
    clearancePercent: 40,
    finalSale: true,
    variants: [],
  },
  {
    styleId: '471902',
    skuCode: '471902-004-29',
    name: 'High Rise Straight Jean',
    description: 'High Rise Straight Jean',
    department: "WOMEN'S",
    category: 'DENIM',
    colorName: 'Medium Indigo',
    colorCode: '#41597d',
    size: '29 Reg',
    listPrice: 69.95,
    salePrice: 69.95,
    clearancePercent: 0,
    finalSale: false,
    variants: [],
  },
  {
    styleId: '512884',
    skuCode: '512884-022-M',
    name: 'Logo Fleece Hoodie',
    description: 'Logo Fleece Hoodie',
    department: "MEN'S",
    category: 'FLEECE',
    colorName: 'Navy Uniform',
    colorCode: '#1f2a44',
    size: 'M',
    listPrice: 59.95,
    salePrice: 41.97,
    clearancePercent: 30,
    finalSale: false,
    variants: [],
  },
];

export const MOCK_INVENTORY: Record<string, Inventory> = {
  '268341-016-L': {
    skuCode: '268341-016-L',
    storeId: STORE_ID,
    onHand: 23,
    nearbyStores: [
      { storeId: '1042', storeName: 'Union Square', distanceMiles: 2.4, onHand: 11 },
      { storeId: '1177', storeName: 'Stonestown', distanceMiles: 5.1, onHand: 6 },
      { storeId: '1284', storeName: 'Serramonte', distanceMiles: 8.3, onHand: 4 },
      { storeId: '1391', storeName: 'Bay Street', distanceMiles: 9.6, onHand: 2 },
    ],
    shipFromStoreEligible: true,
    floor: 'Floor 1',
    fixture: 'Fixture T-12',
  },
  '471902-004-29': {
    skuCode: '471902-004-29',
    storeId: STORE_ID,
    onHand: 7,
    nearbyStores: [
      { storeId: '1042', storeName: 'Union Square', distanceMiles: 2.4, onHand: 5 },
      { storeId: '1177', storeName: 'Stonestown', distanceMiles: 5.1, onHand: 3 },
    ],
    shipFromStoreEligible: true,
    floor: 'Floor 2',
    fixture: 'Fixture D-04',
  },
  '512884-022-M': {
    skuCode: '512884-022-M',
    storeId: STORE_ID,
    onHand: 12,
    nearbyStores: [
      { storeId: '1042', storeName: 'Union Square', distanceMiles: 2.4, onHand: 9 },
      { storeId: '1284', storeName: 'Serramonte', distanceMiles: 8.3, onHand: 1 },
    ],
    shipFromStoreEligible: false,
    floor: 'Floor 2',
    fixture: 'Fixture F-07',
  },
};

export const MOCK_PROMOTIONS: Promotion[] = [
  { code: 'FALL30', description: '30% off', percentOff: 30, amountOff: 0 },
];

function lineItemFor(skuCode: string, quantity: number): OrderLineItem {
  const product = MOCK_PRODUCTS.find((p) => p.skuCode === skuCode)!;
  const inventory = MOCK_INVENTORY[skuCode];
  return {
    skuCode: product.skuCode,
    styleId: product.styleId,
    description: product.description,
    department: `${product.department} · ${product.category}`,
    colorName: product.colorName,
    colorCode: product.colorCode,
    size: product.size,
    quantity,
    listPrice: product.listPrice,
    unitPrice: product.salePrice,
    extendedPrice: product.salePrice * quantity,
    discountReason: product.clearancePercent > 0 ? `Clearance ${product.clearancePercent}%` : null,
    clearancePercent: product.clearancePercent,
    finalSale: product.finalSale,
    inventory,
  };
}

export const MOCK_LINE_ITEMS: OrderLineItem[] = [
  lineItemFor('268341-016-L', 1),
  lineItemFor('471902-004-29', 1),
  lineItemFor('512884-022-M', 1),
];

export const MOCK_SERVICES_AND_FEES = 12.0;

export function mockLineItem(skuCode: string, quantity = 1): OrderLineItem | null {
  if (!MOCK_PRODUCTS.some((p) => p.skuCode === skuCode)) return null;
  return lineItemFor(skuCode, quantity);
}
