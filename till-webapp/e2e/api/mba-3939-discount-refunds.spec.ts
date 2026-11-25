import { expect, test, type APIRequestContext } from '@playwright/test'

// MBA-3939 x MBA-3933 / MBA-3934: a refund pays back what the customer paid for the line, not the shelf price.
const MEMBER = '9826000000000001'
const till = () => `refund-disc-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`

async function sale(request: APIRequestContext, barcodes: string[], member?: string) {
  const tillId = till()
  const { basketId } = await (await request.get(`/api/till/session?tillId=${tillId}`)).json()
  for (const barcode of barcodes) {
    expect((await request.post('/api/till/basket/lines', { data: { basketId, barcode } })).status()).toBe(201)
  }
  if (member) {
    expect((await request.post('/api/till/basket/member', { data: { basketId, cardNumber: member } })).status()).toBe(200)
  }
  const basket = await (await request.get(`/api/till/basket/${basketId}`)).json()
  const tender = await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED' } })
  expect(tender.status()).toBe(200)
  const result = await tender.json()
  return { tillId, basket, orderNumber: result.orderNumber as string, receiptNumber: result.receipt.receiptNumber as string }
}

const refund = (request: APIRequestContext, reference: string, skuCode: string, quantity: number) =>
  request.post('/api/till/refund', { data: { tillId: till(), reference, lines: [{ skuCode, quantity }] } })

test('More Card item refunds at the member price paid, with its receipt name', async ({ request }) => {
  const { basket, orderNumber, receiptNumber } = await sale(request, ['1006', '1001'], MEMBER)
  expect(basket).toMatchObject({ subtotalPence: 505, savingsPence: 50, totalPence: 455 })
  const cheddar = basket.lines.find((l: { barcode?: string; unitPricePence: number }) => l.unitPricePence === 350)

  const lookup = await (await request.get(`/api/till/refund/${receiptNumber}`)).json()
  expect(lookup).toMatchObject({ orderNumber, totalPence: 455 })
  expect(lookup.lines).toEqual(expect.arrayContaining([
    expect.objectContaining({ skuCode: cheddar.skuCode, name: cheddar.name, unitPricePence: 300, soldQuantity: 1 }),
    expect.objectContaining({ skuCode: 'MOR-MILK-SS-4PT', name: 'Semi skimmed milk 4 pints', unitPricePence: 155 }),
  ]))

  const refunded = await refund(request, receiptNumber, cheddar.skuCode, 1)
  expect(refunded.status()).toBe(201)
  const result = await refunded.json()
  expect(result).toMatchObject({ status: 'REFUNDED', refundPence: 300 })
  expect(result.lines).toEqual([expect.objectContaining({ name: cheddar.name, quantity: 1 })])
  expect(await (await request.get(`/api/order/${result.refundNumber}`)).json()).toMatchObject({ orderType: 'REFUND', totalPence: -300 })
})

test('multi-buy item refunds its share of the promotion price, never more than the line took', async ({ request }) => {
  const { basket, orderNumber } = await sale(request, ['393407', '393407', '393407'])
  expect(basket).toMatchObject({ subtotalPence: 450, savingsPence: 100, totalPence: 350 })
  const skuCode = basket.lines[0].skuCode as string

  const first = await (await refund(request, orderNumber, skuCode, 1)).json()
  expect(first).toMatchObject({ status: 'REFUNDED', refundPence: 116 })
  expect(first.lines[0].name).toBe(basket.lines[0].name)

  const rest = await (await refund(request, orderNumber, skuCode, 2)).json()
  expect(rest).toMatchObject({ status: 'REFUNDED', refundPence: 234 })
  expect(rest.remaining).toMatchObject({ refundedPence: 350, refundablePence: 0 })
})
