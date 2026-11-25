import { describe, expect, it } from 'vitest'
import { formatPence } from './money'

describe('formatPence', () => {
  it('formats integer pence as GBP', () => {
    expect(formatPence(155)).toBe('£1.55')
    expect(formatPence(0)).toBe('£0.00')
    expect(formatPence(12345)).toBe('£123.45')
  })
})
