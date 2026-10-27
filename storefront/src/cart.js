// Pure cart/price logic shared by the browser UI (served as /cart.js) and the BFF.
// Money is handled in integer cents to avoid floating point drift.

export const MAX_QUANTITY = 99;

export function toCents(price) {
  const value = Number(price);
  if (!Number.isFinite(value) || value < 0) {
    throw new RangeError(`Invalid price: ${price}`);
  }
  return Math.round(value * 100);
}

export function formatCents(cents) {
  return `$${(cents / 100).toFixed(2)}`;
}

export function validateQuantity(quantity) {
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) {
    throw new RangeError(`Quantity must be an integer between 1 and ${MAX_QUANTITY}`);
  }
  return quantity;
}

export function addItem(cart, product, quantity = 1) {
  validateQuantity(quantity);
  if (!product || !product.skuCode) {
    throw new TypeError('Product must have a skuCode to be added to the cart');
  }
  const existing = cart.find((item) => item.skuCode === product.skuCode);
  if (existing) {
    return updateQuantity(cart, product.skuCode, existing.quantity + quantity);
  }
  return [...cart, {
    skuCode: product.skuCode,
    name: product.name,
    priceCents: toCents(product.price),
    quantity,
  }];
}

export function updateQuantity(cart, skuCode, quantity) {
  validateQuantity(quantity);
  if (!cart.some((item) => item.skuCode === skuCode)) {
    throw new RangeError(`SKU ${skuCode} is not in the cart`);
  }
  return cart.map((item) => (item.skuCode === skuCode ? { ...item, quantity } : item));
}

export function removeItem(cart, skuCode) {
  return cart.filter((item) => item.skuCode !== skuCode);
}

export function lineTotalCents(item) {
  return item.priceCents * item.quantity;
}

export function cartTotals(cart) {
  return {
    itemCount: cart.reduce((sum, item) => sum + item.quantity, 0),
    totalCents: cart.reduce((sum, item) => sum + lineTotalCents(item), 0),
  };
}

export function toOrderRequest(cart) {
  if (cart.length === 0) {
    throw new RangeError('Cannot check out an empty cart');
  }
  return {
    orderLineItemsDtoList: cart.map((item) => ({
      skuCode: item.skuCode,
      price: (item.priceCents / 100).toFixed(2),
      quantity: validateQuantity(item.quantity),
    })),
  };
}
