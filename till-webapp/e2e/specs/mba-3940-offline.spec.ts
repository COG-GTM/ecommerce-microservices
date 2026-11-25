import { expect, test } from '../fixtures/offline'

// MBA-3940: the till keeps selling cash from its cached catalogue while it cannot reach the backend, queues the
// sales locally and replays them once on reconnect. The network cut is the browser's own (no responses are mocked).
test.describe('MBA-3940 offline resilience', () => {
  test('offline cash sale from the cached catalogue syncs exactly once on reconnect @tablet', async ({ till, offlineTill: offline, offlineBackend, backend }) => {
    await till.open()
    await till.quickKey('1001')
    await till.expectLine('Semi skimmed milk 4 pints', 1, '£1.55', '£1.55')

    await offline.cutNetwork()
    await offline.expectOffline()
    await till.expectLine('Semi skimmed milk 4 pints', 1, '£1.55', '£1.55')
    await till.quickKey('1004')
    await till.keyBarcode('1001')
    await till.expectLine('Semi skimmed milk 4 pints', 2, '£1.55', '£3.10')
    await till.expectLine('Bananas x5', 1, '£0.95', '£0.95')
    await expect(till.total).toHaveText('£4.05')
    await till.scan('5000000000000')
    await expect(till.scanMessage).toContainText('not available offline')

    await till.pay()
    await offline.expectCardUnavailable()
    await offline.payCashOffline(5)
    await offline.expectChange('£0.95')
    await offline.expectQueued(1)

    await offline.restoreNetwork()
    await offline.expectOnline()
    await offline.expectQueued(0)
    const [order] = await offlineBackend.expectOrdersForTill(till.tillId, 1)
    expect(order).toMatchObject({ channel: 'TILL', tenderType: 'CASH', totalPence: 405 })
    expect(order.idempotencyKey).toMatch(/^offline-/)
    expect(order.transactionId).toMatch(/-OFF\d+$/)
    await backend.notification(order.orderNumber)

    // The server basket left open when the till went offline was voided, so the next sale starts empty and online.
    await offline.startNewSale()
    await expect(till.lines).toHaveCount(0)
    await till.quickKey('1004')
    await till.pay()
    await offline.expectCardAvailable()
    await till.payByCard('approved')
    const cardOrder = await till.orderNumber()
    expect(await backend.order(cardOrder)).toMatchObject({ tenderType: 'CARD', totalPence: 95 })
    await offlineBackend.expectOrdersForTill(till.tillId, 2)
    await offline.expectNoOfflineReceipt()
  })

  test('several offline sales queue in order and each syncs once', async ({ till, offlineTill: offline, offlineBackend }) => {
    await till.open()
    await offline.cutNetwork()
    await offline.expectOffline()

    await till.quickKey('1001')
    await till.pay()
    await offline.payCashOffline()
    await offline.startNewSale()
    await expect(till.lines).toHaveCount(0)
    await till.quickKey('1004')
    await till.quickKey('1004')
    await till.pay()
    await offline.payCashOffline(10)
    await offline.expectChange('£8.10')
    await offline.expectQueued(2)

    await offline.restoreNetwork()
    await offline.expectOnline()
    await offline.expectQueued(0)
    const orders = await offlineBackend.expectOrdersForTill(till.tillId, 2)
    expect(orders.map((o) => o.totalPence)).toEqual([155, 190])
    expect(new Set(orders.map((o) => o.idempotencyKey)).size).toBe(2)
  })

  test('card already chosen is withdrawn when the till goes offline; the sale is finished in cash', async ({ till, offlineTill: offline, offlineBackend }) => {
    await till.open()
    await till.quickKey('1001')
    await till.pay()
    await offline.chooseCard()
    await offline.cutNetwork()
    await offline.expectOffline()
    await offline.expectPinpadClosed()
    await offline.expectCardUnavailable()

    // The basket scanned online is finished offline in cash and syncs once.
    await offline.payCashOffline(5)
    await offline.expectChange('£3.45')
    await offline.restoreNetwork()
    await offline.expectQueued(0)
    const [order] = await offlineBackend.expectOrdersForTill(till.tillId, 1)
    expect(order).toMatchObject({ tenderType: 'CASH', totalPence: 155 })
  })
})
