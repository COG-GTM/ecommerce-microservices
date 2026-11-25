import { GATEWAY_URL } from '../fixtures/backend'
import { expect, LOCAL_STACK, test } from '../fixtures/offline'

// MBA-3940 acceptance: stop the real API gateway mid-shift, complete a cash sale, restart the gateway and verify
// exactly one synced order. Runs in the `outage` project after every other project, because it takes the gateway down.
test.skip(!LOCAL_STACK, 'stops the local docker compose gateway; TILL_URL points at another stack')

test('gateway outage mid-shift: cash sale is queued and synced exactly once @outage', async ({ till, offlineTill: offline, offlineBackend, backend, stack, request, page }) => {
  test.setTimeout(300_000)
  await till.open()
  await till.quickKey('1001')

  stack.stopGateway()
  await offline.expectOffline()
  await till.quickKey('1004')
  await expect(till.total).toHaveText('£2.50')
  await till.pay()
  await offline.expectCardUnavailable()
  await offline.payCashOffline(5)
  await offline.expectChange('£2.50')
  await offline.expectQueued(1)

  // Order responses never carry the idempotency key (MBA-3938), so read the queued sale's id from the till itself.
  const saleId = await page.evaluate(() => {
    for (const key of Object.keys(localStorage)) {
      try {
        const value = JSON.parse(localStorage.getItem(key) ?? 'null')
        if (Array.isArray(value) && typeof value[0]?.saleId === 'string') return value[0].saleId as string
      } catch {
        // not JSON
      }
    }
    return null
  })
  expect(saleId, 'queued offline sale id').toBeTruthy()

  stack.startGateway()
  await offline.expectOnline(180_000)
  await offline.expectQueued(0, 60_000)
  const [order] = await offlineBackend.expectOrdersForTill(till.tillId, 1)
  expect(order).toMatchObject({ tenderType: 'CASH', totalPence: 250 })
  await backend.notification(order.orderNumber)

  // Replaying the same sale again (e.g. a retry after a lost response) returns the same order and stores nothing new.
  const replay = await request.post(`${GATEWAY_URL}/api/till/offline-sales`, {
    data: { saleId, tillId: till.tillId, transactionNumber: order.transactionId, tenderType: 'CASH', amountTenderedPence: 500, lines: [{ barcode: '1001', quantity: 1 }, { barcode: '1004', quantity: 1 }] },
  })
  expect(replay.status()).toBe(200)
  expect((await replay.json()).orderNumber).toBe(order.orderNumber)
  await offlineBackend.expectOrdersForTill(till.tillId, 1)
})
