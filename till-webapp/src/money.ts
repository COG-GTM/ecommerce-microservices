const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' })

/** All amounts are integer pence; format only at the edge. */
export const formatPence = (pence: number): string => gbp.format(pence / 100)

/** Grams as kg with 3 decimals, e.g. 452 -> "0.452 kg". */
export const formatKg = (grams: number): string => (grams / 1000).toFixed(3) + ' kg'

/** Parses a keyed kg weight ("0.452", ".5", "1") to whole grams; null if not a positive weight. */
export const parseKgToGrams = (kg: string): number | null => {
  if (!/^\d*\.?\d{0,3}$/.test(kg) || !/\d/.test(kg)) return null
  const grams = Math.round(Number(kg) * 1000)
  return grams > 0 ? grams : null
}
