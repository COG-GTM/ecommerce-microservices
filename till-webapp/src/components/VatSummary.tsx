import type { VatBand } from '../api/types'
import { formatPence } from '../money'

/** Server-computed VAT breakdown per rate band, shown on the basket and the receipt. */
export function VatSummary({ bands, testId }: { bands: VatBand[]; testId: string }) {
  if (bands.length === 0) return null
  return (
    <table className="vat-summary" data-testid={testId} aria-label="VAT summary">
      <thead>
        <tr><th>VAT</th><th className="num">Net</th><th className="num">VAT</th><th className="num">Gross</th></tr>
      </thead>
      <tbody>
        {bands.map((band) => (
          <tr key={band.code} data-testid="vat-band" data-vat-code={band.code}>
            <td data-testid="vat-rate">{band.code} {band.ratePercent}%</td>
            <td className="num" data-testid="vat-net">{formatPence(band.netPence)}</td>
            <td className="num" data-testid="vat-amount">{formatPence(band.vatPence)}</td>
            <td className="num" data-testid="vat-gross">{formatPence(band.grossPence)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
