import { expect, test, type APIRequestContext } from '@playwright/test'

// MBA-3933 More Card loyalty through the gateway: member attach, member prices as savings, points on the receipt.
const MEMBER = '9826000000000001'

async function basketWith(request: APIRequestContext, barcodes: string[]) {
  const session = await (await request.get(`/api/till/session?tillId=api-mc-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`)).json()
  for (const barcode of barcodes) {
    expect((await request.post('/api/till/basket/lines', { data: { basketId: session.basketId, barcode } })).status()).toBe(201)
  }
  return session.basketId as string
}

const pay = async (request: APIRequestContext, basketId: string) =>
  (await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED' } })).json()

test('member vs non-member: totals, savings and server-side points', async ({ request }) => {
  const memberBasket = await basketWith(request, ['1006', '1001'])
  const attached = await request.post('/api/till/basket/member', { data: { basketId: memberBasket, cardNumber: MEMBER } })
  expect(attached.status()).toBe(200)
  const basket = await attached.json()
  expect(basket).toMatchObject({ member: { cardLast4: '0001', name: 'Test Member One' }, subtotalPence: 505, savingsPence: 50, totalPence: 455 })
  expect(basket.adjustments).toEqual([expect.objectContaining({ code: 'MORE_CARD', amountPence: -50 })])
  expect(JSON.stringify(basket)).not.toContain(MEMBER)
  const member = await pay(request, memberBasket)
  expect(member.receipt).toMatchObject({ totalPence: 455, savingsPence: 50, moreCard: { cardLast4: '0001', pointsEarned: 20 } })
  expect((await (await request.get(`/api/order/${member.orderNumber}`)).json()).totalPence).toBe(455)

  const plainBasket = await basketWith(request, ['1006', '1001'])
  const plain = await pay(request, plainBasket)
  expect(plain.receipt).toMatchObject({ totalPence: 505, savingsPence: 0, moreCard: null })
})

test('unknown card is 404 UNKNOWN_CARD and the basket stays a non-member sale', async ({ request }) => {
  const basketId = await basketWith(request, ['1006'])
  const unknown = await request.post('/api/till/basket/member', { data: { basketId, cardNumber: '9826999999999999' } })
  expect(unknown.status()).toBe(404)
  expect(await unknown.json()).toMatchObject({ error: 'UNKNOWN_CARD' })
  expect(await (await request.get(`/api/till/basket/${basketId}`)).json()).toMatchObject({ member: null, totalPence: 350 })
  expect((await pay(request, basketId)).status).toBe('APPROVED')
})
