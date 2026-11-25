const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' })

/** All amounts are integer pence; format only at the edge. */
export const formatPence = (pence: number): string => gbp.format(pence / 100)
