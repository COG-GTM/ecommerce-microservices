import { expect, test, type APIRequestContext } from '@playwright/test'

// MBA-3934 promotions through the API gateway: real till-service pricing, product seeds, order persistence.
const till = () => `api-3934-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`

async function newBasketId(request: APIRequestContext) {
  const session = await request.get(`/api/till/session?tillId=${till()}`)
  expect(session.status()).toBe(200)
  return (await session.json()).basketId as string
}

async function scan(request: APIRequestContext, basketId: string, barcode: string) {
  const response = await request.post('/api/till/basket/lines', { data: { basketId, barcode } })
  expect(response.status()).toBe(201)
  return response.json()
}

test('promotion products are seeded and are not quick keys', async ({ request }) => {
  expect(await (await request.get('/api/till/products/393407')).json()).toMatchObject({
    skuCode: 'MOR-YOGHURT-GREEK-500', pricePence: 150, quickKey: false,
  })
  const quickKeys = await (await request.get('/api/till/products?quickkey=true')).json()
  expect(quickKeys.map((p: { barcode: string }) => p.barcode)).not.toContain('393407')
})

test('3 for £3.50 adds a MULTIBUY adjustment on the third unit and removes it on void', async ({ request }) => {
  const basketId = await newBasketId(request)
  await scan(request, basketId, '393407')
  expect(await scan(request, basketId, '393407')).toMatchObject({ adjustments: [], savingsPence: 0, totalPence: 300 })
  const basket = await scan(request, basketId, '393407')
  const lineId = basket.lines[0].lineId
  expect(basket).toMatchObject({
    subtotalPence: 450, savingsPence: 100, totalPence: 350,
    adjustments: [{ code: 'MULTIBUY', label: 'Greek yoghurt 3 for £3.50', amountPence: -100, lineId }],
  })
  const voided = await (await request.delete(`/api/till/basket/lines/${lineId}?basketId=${basketId}`)).json()
  expect(voided).toMatchObject({ adjustments: [], savingsPence: 0, totalPence: 0 })
})

test('best price: cola goes to the meal deal and the order is persisted at the discounted total', async ({ request }) => {
  const basketId = await newBasketId(request)
  for (const barcode of ['393406', '393406', '393401', '393403']) await scan(request, basketId, barcode)
  const basket = await (await request.get(`/api/till/basket/${basketId}`)).json()
  expect(basket.adjustments).toEqual([
    expect.objectContaining({ code: 'MEAL_DEAL', label: 'Meal deal £3.50', amountPence: -170 }),
  ])
  expect(basket).toMatchObject({ subtotalPence: 655, savingsPence: 170, totalPence: 485 })

  const tender = await (await request.post('/api/till/basket/tender', {
    data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED', expectedTotalPence: 485 },
  })).json()
  expect(tender).toMatchObject({ status: 'APPROVED', receipt: { savingsPence: 170, totalPence: 485 } })
  const order = await (await request.get(`/api/order/${tender.orderNumber}`)).json()
  expect(order.totalPence).toBe(485)
})
