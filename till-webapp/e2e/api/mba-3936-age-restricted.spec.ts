import { expect, test, type APIRequestContext } from '@playwright/test'

// MBA-3936: age-restricted items through the gateway against the real services.
const till = () => `api-3936-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`

async function basketWithLager(request: APIRequestContext) {
  const { basketId } = await (await request.get(`/api/till/session?tillId=${till()}`)).json()
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1001' } })
  const basket = await (await request.post('/api/till/basket/lines', { data: { basketId, barcode: '3001' } })).json()
  return { basketId: basketId as string, basket }
}

const ageCheck = (request: APIRequestContext, basketId: string, lineId: string, decision: string, operatorId?: string) =>
  request.post(`/api/till/basket/lines/${lineId}/age-check`, { data: { basketId, decision, operatorId } })

test('seeded alcohol carries a minimum age and is not a quick key', async ({ request }) => {
  expect(await (await request.get('/api/till/products/3001')).json()).toMatchObject({
    name: 'Lager 4 x 440ml', skuCode: 'MOR-LAGER-4X440', pricePence: 450, minimumAge: 18, quickKey: false,
  })
  expect(await (await request.get('/api/till/products/3002')).json()).toMatchObject({ minimumAge: 18 })
  expect(await (await request.get('/api/till/products/1001')).json()).toMatchObject({ minimumAge: null })
})

test('restricted line is pending; tender is 409 AGE_CHECK_REQUIRED until approved; order records the operator', async ({ request }) => {
  const { basketId, basket } = await basketWithLager(request)
  const lager = basket.lines.find((l: { barcode: string }) => l.barcode === '3001')
  expect(lager).toMatchObject({ minimumAge: 18, ageCheck: 'PENDING', ageApprovedBy: null })
  expect(basket.pendingAgeChecks).toBe(1)

  const blocked = await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED' } })
  expect(blocked.status()).toBe(409)
  expect(await blocked.json()).toMatchObject({ error: 'AGE_CHECK_REQUIRED' })

  const approved = await (await ageCheck(request, basketId, lager.lineId, 'APPROVED', 'OP-2002')).json()
  expect(approved.pendingAgeChecks).toBe(0)
  expect(approved.lines[1]).toMatchObject({ ageCheck: 'APPROVED', ageApprovedBy: 'OP-2002' })

  const sale = await (await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED' } })).json()
  expect(sale).toMatchObject({ status: 'APPROVED', receipt: { totalPence: 605 } })
  const order = await (await request.get(`/api/order/${sale.orderNumber}`)).json()
  expect(order.lines).toEqual(expect.arrayContaining([
    expect.objectContaining({ skuCode: 'MOR-LAGER-4X440', minimumAge: 18, ageApprovedBy: 'OP-2002' }),
  ]))
})

test('refused line is removed from the basket', async ({ request }) => {
  const { basketId, basket } = await basketWithLager(request)
  const refused = await (await ageCheck(request, basketId, basket.lastLineId, 'REFUSED')).json()
  expect(refused).toMatchObject({ pendingAgeChecks: 0, totalPence: 155 })
  expect(refused.lines.map((l: { barcode: string }) => l.barcode)).toEqual(['1001'])
})

test('invalid age-check requests are rejected', async ({ request }) => {
  const { basketId, basket } = await basketWithLager(request)
  const bad = await ageCheck(request, basketId, basket.lastLineId, 'MAYBE')
  expect(bad.status()).toBe(400)
  expect(await bad.json()).toMatchObject({ error: 'INVALID_AGE_DECISION' })
  const milk = await ageCheck(request, basketId, basket.lines[0].lineId, 'APPROVED')
  expect(milk.status()).toBe(422)
  expect(await milk.json()).toMatchObject({ error: 'NOT_AGE_RESTRICTED' })
})
