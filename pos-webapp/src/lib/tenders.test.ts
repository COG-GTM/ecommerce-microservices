import { describe, expect, it } from 'vitest';
import { parseAmount, tenderLabel } from './tenders';

describe('tenders', () => {
  it('parses keyed amounts to cents and treats junk as zero', () => {
    expect(parseAmount('100')).toBe(100);
    expect(parseAmount('12.345')).toBe(12.35);
    expect(parseAmount('')).toBe(0);
    expect(parseAmount('abc')).toBe(0);
  });

  it('labels the cash tender', () => {
    expect(tenderLabel('CASH')).toBe('Cash');
  });
});
