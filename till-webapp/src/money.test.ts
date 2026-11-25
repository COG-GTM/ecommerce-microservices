import { describe, expect, it } from 'vitest'
import { formatKg, formatPence, parseKgToGrams } from './money'

describe('formatPence', () => {
  it('formats integer pence as GBP', () => {
    expect(formatPence(155)).toBe('£1.55')
    expect(formatPence(0)).toBe('£0.00')
    expect(formatPence(12345)).toBe('£123.45')
  })
})

describe('weights', () => {
  it('formats grams as kg', () => {
    expect(formatKg(452)).toBe('0.452 kg')
    expect(formatKg(1250)).toBe('1.250 kg')
  })
  it('parses keyed kg to grams', () => {
    expect(parseKgToGrams('0.452')).toBe(452)
    expect(parseKgToGrams('.5')).toBe(500)
    expect(parseKgToGrams('1')).toBe(1000)
    expect(parseKgToGrams('0')).toBeNull()
    expect(parseKgToGrams('.')).toBeNull()
    expect(parseKgToGrams('1.2345')).toBeNull()
    expect(parseKgToGrams('')).toBeNull()
  })
})
