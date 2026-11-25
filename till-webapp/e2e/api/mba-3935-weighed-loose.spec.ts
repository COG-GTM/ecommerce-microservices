import { expect, test, type APIRequestContext } from '@playwright/test'

const till = () => `api-3935-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`

async function newBasket(request: APIRequestContext) {
  return (await (await request.get(`/api/till/session?tillId=${till()}`)).json()) as { basketId: string }
}

test.describe('MBA-3935 weighed and loose items API', () => {
  test('weighed PLUs are seeded per kg and are not quick keys', async ({ request }) => {
    expect(await (await request.get('/api/till/products/2201')).json())
      .toMatchObject({ name: 'Loose tomatoes', pricePence: 220, soldByWeight: true, quickKey: false })
    const quick = await (await request.get('/api/till/products?quickkey=true')).json()
    expect(quick.map((p: { barcode: string }) => p.barcode)).not.toContain('2201')
  })

  test('embedded-price label, manual weight and bad check digit', async ({ request }) => {
    const { basketId } = await newBasket(request)
    const label = await request.post('/api/till/basket/lines', { data: { basketId, barcode: '2002101003490' } })
    expect(label.status()).toBe(201)
    expect((await label.json()).lines[0]).toMatchObject({ pricedBy: 'LABEL', skuCode: 'MOR-DELI-HAM-KG', unitPricePence: 349, linePence: 349 })

    const prompt = await request.post('/api/till/basket/lines', { data: { basketId, barcode: '2201' } })
    expect(prompt.status()).toBe(422)
    expect(await prompt.json()).toMatchObject({ error: 'WEIGHT_REQUIRED' })

    const weighed = await (await request.post('/api/till/basket/lines', { data: { basketId, barcode: '2201', weightGrams: 452 } })).json()
    expect(weighed.lines[1]).toMatchObject({ pricedBy: 'WEIGHT', weightGrams: 452, unitPricePence: 220, linePence: 99 })
    expect(weighed.totalPence).toBe(448)

    const bad = await request.post('/api/till/basket/lines', { data: { basketId, barcode: '2002101003491' } })
    expect(bad.status()).toBe(422)
    expect(await bad.json()).toMatchObject({ error: 'INVALID_CHECK_DIGIT', message: expect.stringContaining('invalid check digit') })

    const approved = await (await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED' } })).json()
    expect(approved).toMatchObject({ status: 'APPROVED', receipt: { totalPence: 448 } })
    const order = await (await request.get(`/api/order/${approved.orderNumber}`)).json()
    expect(order.totalPence).toBe(448)
  })
})
