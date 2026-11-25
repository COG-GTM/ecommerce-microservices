import { expect, test } from '@playwright/test'

// MBA-3932: VAT codes on the catalogue and a server-computed VAT summary per rate on basket and receipt.
const till = () => `api-vat-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`

test('catalogue carries VAT codes; standard-rated barcodes are seeded', async ({ request }) => {
  expect(await (await request.get('/api/till/products/1001')).json()).toMatchObject({ vatCode: 'Z' })
  expect(await (await request.get('/api/till/products/4001')).json()).toMatchObject({ vatCode: 'S', pricePence: 120 })
  expect(await (await request.get('/api/till/products/4002')).json()).toMatchObject({ vatCode: 'S', pricePence: 135 })
})

test('VAT summary is recomputed when a line quantity changes or a line is voided', async ({ request }) => {
  const { basketId } = await (await request.get(`/api/till/session?tillId=${till()}`)).json()
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1001' } })
  const basket = await (await request.post('/api/till/basket/lines', { data: { basketId, barcode: '4001' } })).json()
  const milk = basket.lines[0].lineId
  const chocolate = basket.lines[1].lineId

  const updated = await (await request.patch(`/api/till/basket/lines/${chocolate}`, { data: { basketId, quantity: 3 } })).json()
  expect(updated.totalPence).toBe(515)
  expect(updated.vatSummary).toEqual([
    { code: 'Z', ratePercent: 0, netPence: 155, vatPence: 0, grossPence: 155 },
    { code: 'S', ratePercent: 20, netPence: 300, vatPence: 60, grossPence: 360 },
  ])

  const voided = await (await request.delete(`/api/till/basket/lines/${milk}?basketId=${basketId}`)).json()
  expect(voided.totalPence).toBe(360)
  expect(voided.vatSummary).toEqual([{ code: 'S', ratePercent: 20, netPence: 300, vatPence: 60, grossPence: 360 }])
})

test('mixed basket and receipt carry a VAT summary per rate in integer pence', async ({ request }) => {
  const { basketId } = await (await request.get(`/api/till/session?tillId=${till()}`)).json()
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1001' } })
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '4001', quantity: 2 } })
  const basket = await (await request.post('/api/till/basket/lines', { data: { basketId, barcode: '4002' } })).json()
  const expected = [
    { code: 'Z', ratePercent: 0, netPence: 155, vatPence: 0, grossPence: 155 },
    { code: 'S', ratePercent: 20, netPence: 312, vatPence: 63, grossPence: 375 },
  ]
  expect(basket.totalPence).toBe(530)
  expect(basket.vatSummary).toEqual(expected)
  expect(basket.lines.map((l: { vatCode: string }) => l.vatCode)).toEqual(['Z', 'S', 'S'])

  const tender = await (await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED' } })).json()
  expect(tender.receipt.totalPence).toBe(530)
  expect(tender.receipt.vatSummary).toEqual(expected)
})
