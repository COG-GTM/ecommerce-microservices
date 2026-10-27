import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  MAX_QUANTITY, addItem, cartTotals, formatCents, lineTotalCents, removeItem, toCents, toOrderRequest,
  updateQuantity, validateQuantity,
} from '../src/cart.js';

const phone = { id: 'p1', skuCode: 'iphone_15', name: 'iPhone 15', price: 799.99 };
const case_ = { id: 'p2', skuCode: 'case', name: 'Case', price: '19.5' };
const phoneRefurb = { id: 'p3', skuCode: 'iphone_15', name: 'iPhone 15 (refurbished)', price: 499.5 };

describe('money helpers', () => {
  test('toCents rounds decimal and string prices to integer cents', () => {
    assert.equal(toCents(799.99), 79999);
    assert.equal(toCents('19.5'), 1950);
    assert.equal(toCents(0.1 + 0.2), 30);
    assert.equal(toCents(0), 0);
  });

  test('toCents rejects negative, non-numeric and infinite prices', () => {
    for (const bad of [-1, 'abc', Infinity, undefined]) {
      assert.throws(() => toCents(bad), RangeError);
    }
  });

  test('formatCents renders dollars with two decimals', () => {
    assert.equal(formatCents(0), '$0.00');
    assert.equal(formatCents(161948), '$1619.48');
  });
});

describe('validateQuantity', () => {
  test('accepts boundaries 1 and MAX_QUANTITY', () => {
    assert.equal(validateQuantity(1), 1);
    assert.equal(validateQuantity(MAX_QUANTITY), MAX_QUANTITY);
  });

  test('rejects zero, negatives, fractions, over-max and non-numbers', () => {
    for (const bad of [0, -2, 1.5, MAX_QUANTITY + 1, '2', NaN]) {
      assert.throws(() => validateQuantity(bad), RangeError);
    }
  });
});

describe('cart operations', () => {
  test('addItem appends a new line with price in cents and default quantity 1', () => {
    assert.deepEqual(addItem([], phone), [{ productId: 'p1', skuCode: 'iphone_15', name: 'iPhone 15', priceCents: 79999, quantity: 1 }]);
  });

  test('products sharing one SKU stay separate lines at their own prices', () => {
    const cart = addItem(addItem([], phoneRefurb), phone);
    assert.deepEqual(cart.map((i) => [i.productId, i.priceCents]), [['p3', 49950], ['p1', 79999]]);
    assert.deepEqual(cartTotals(cart), { itemCount: 2, totalCents: 49950 + 79999 });
  });

  test('addItem merges quantities for the same product without mutating the input', () => {
    const cart = addItem([], phone, 2);
    const merged = addItem(cart, phone, 3);
    assert.equal(merged.length, 1);
    assert.equal(merged[0].quantity, 5);
    assert.equal(cart[0].quantity, 2);
  });

  test('addItem refuses products without a SKU and merges that would exceed the max', () => {
    assert.throws(() => addItem([], { id: 'x', name: 'No SKU', price: 1 }), TypeError);
    assert.throws(() => addItem([], { skuCode: 'x', name: 'No id', price: 1 }), TypeError);
    assert.throws(() => addItem([], null), TypeError);
    assert.throws(() => addItem(addItem([], phone, MAX_QUANTITY), phone, 1), RangeError);
    assert.throws(() => addItem([], phone, 0), RangeError);
  });

  test('updateQuantity changes only the targeted line', () => {
    const cart = addItem(addItem([], phone), case_);
    const updated = updateQuantity(cart, 'p2', 4);
    assert.deepEqual(updated.map((i) => i.quantity), [1, 4]);
  });

  test('updateQuantity rejects unknown SKUs and invalid quantities', () => {
    const cart = addItem([], phone);
    assert.throws(() => updateQuantity(cart, 'missing', 1), /not in the cart/);
    assert.throws(() => updateQuantity(cart, 'p1', 0), RangeError);
  });

  test('removeItem drops the SKU and is a no-op for unknown SKUs', () => {
    const cart = addItem(addItem([], phone), case_);
    assert.deepEqual(removeItem(cart, 'p1').map((i) => i.productId), ['p2']);
    assert.equal(removeItem(cart, 'missing').length, 2);
  });

  test('cartTotals sums quantities and line totals in cents', () => {
    const cart = addItem(addItem([], phone, 2), case_, 3);
    assert.equal(lineTotalCents(cart[0]), 159998);
    assert.deepEqual(cartTotals(cart), { itemCount: 5, totalCents: 159998 + 5850 });
    assert.deepEqual(cartTotals([]), { itemCount: 0, totalCents: 0 });
  });
});

describe('toOrderRequest', () => {
  test('maps cart lines to the order-service OrderRequest contract', () => {
    const cart = addItem(addItem([], phone, 2), case_);
    assert.deepEqual(toOrderRequest(cart), {
      orderLineItemsDtoList: [
        { skuCode: 'iphone_15', price: '799.99', quantity: 2 },
        { skuCode: 'case', price: '19.50', quantity: 1 },
      ],
    });
  });

  test('rejects an empty cart and corrupted quantities', () => {
    assert.throws(() => toOrderRequest([]), /empty cart/);
    assert.throws(() => toOrderRequest([{ skuCode: 'x', priceCents: 1, quantity: 0 }]), RangeError);
  });
});
