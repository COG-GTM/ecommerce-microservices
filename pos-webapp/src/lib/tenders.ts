import type { TenderType } from '../api/types';
import { roundCurrency } from './totals';

export interface TenderLine {
  type: TenderType;
  amount: string;
}

export const TENDERS: Array<{ type: TenderType; label: string; hint: string }> = [
  { type: 'CREDIT_DEBIT', label: 'Credit / debit', hint: 'Insert, tap or swipe' },
  { type: 'GIFT_CARD', label: 'Gift card', hint: 'Scan or key card number' },
  { type: 'MOBILE_WALLET', label: 'Mobile wallet', hint: 'Apple Pay, Google Pay' },
  { type: 'CASH', label: 'Cash', hint: 'Count drawer, give change' },
];

export function tenderLabel(type: TenderType): string {
  return TENDERS.find((tender) => tender.type === type)?.label ?? type;
}

export function parseAmount(value: string): number {
  const amount = Number.parseFloat(value);
  return Number.isFinite(amount) ? roundCurrency(amount) : 0;
}
