import { addItem, cartTotals, formatCents, lineTotalCents, removeItem, updateQuantity } from '/cart.js';

const state = { products: [], cart: [] };
const $ = (id) => document.getElementById(id);

function el(tag, attrs = {}, text = '') {
  const node = document.createElement(tag);
  Object.entries(attrs).forEach(([key, value]) => node.setAttribute(key, value));
  node.textContent = text;
  return node;
}

function renderProducts() {
  const list = $('product-list');
  list.replaceChildren();
  state.products.forEach((product) => {
    const card = el('li', { class: 'product', 'data-testid': 'product-card', 'data-sku': product.skuCode ?? '' });
    card.append(el('h3', {}, product.name), el('p', {}, product.description ?? ''),
      el('p', { class: 'price' }, formatCents(Math.round(Number(product.price) * 100))));
    const button = el('button', { type: 'button', 'aria-label': `Add ${product.name} to cart` }, 'Add to cart');
    button.disabled = !product.skuCode;
    button.addEventListener('click', () => {
      state.cart = addItem(state.cart, product, 1);
      renderCart();
    });
    card.append(button);
    list.append(card);
  });
  $('catalog-status').textContent = state.products.length ? `${state.products.length} products` : 'No products available.';
}

function renderCart() {
  const tbody = $('cart-items');
  tbody.replaceChildren();
  state.cart.forEach((item) => {
    const row = el('tr', { 'data-testid': 'cart-row', 'data-sku': item.skuCode });
    const qty = el('input', { type: 'number', min: '1', max: '99', value: String(item.quantity), 'aria-label': `Quantity for ${item.name}` });
    qty.addEventListener('change', () => {
      try {
        state.cart = updateQuantity(state.cart, item.productId, Number(qty.value));
      } catch {
        // Invalid or blank input: re-render to restore the quantity that will actually be ordered.
      }
      renderCart();
    });
    const remove = el('button', { type: 'button', class: 'secondary', 'aria-label': `Remove ${item.name}` }, 'Remove');
    remove.addEventListener('click', () => {
      state.cart = removeItem(state.cart, item.productId);
      renderCart();
    });
    const qtyCell = el('td');
    qtyCell.append(qty);
    const removeCell = el('td');
    removeCell.append(remove);
    row.append(el('td', {}, item.name), el('td', {}, formatCents(item.priceCents)), qtyCell,
      el('td', { 'data-testid': 'line-total' }, formatCents(lineTotalCents(item))), removeCell);
    tbody.append(row);
  });
  const { itemCount, totalCents } = cartTotals(state.cart);
  $('cart-count').textContent = String(itemCount);
  $('cart-total').textContent = formatCents(totalCents);
  $('cart-table').hidden = state.cart.length === 0;
  $('cart-empty').hidden = state.cart.length > 0;
  $('checkout').disabled = state.cart.length === 0;
}

async function loadProducts() {
  try {
    const response = await fetch('/api/products');
    const body = await response.json();
    if (!response.ok) throw new Error(body.message);
    state.products = body;
    renderProducts();
  } catch (error) {
    $('catalog-status').textContent = `Could not load products: ${error.message}`;
  }
}

async function checkout() {
  const error = $('checkout-error');
  error.hidden = true;
  $('checkout').disabled = true;
  $('checkout').textContent = 'Placing order…';
  try {
    const response = await fetch('/api/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        items: state.cart.map((item) => ({ productId: item.productId, quantity: item.quantity })),
      }),
    });
    const body = await response.json();
    if (response.status !== 201) throw new Error(body.message);
    $('confirmation-message').textContent = body.message;
    $('confirmation-items').replaceChildren(...body.items.map((item) => el('li', {}, `${item.quantity} × ${item.name}`)));
    $('confirmation-total').textContent = formatCents(body.totalCents);
    $('confirmation').hidden = false;
    state.cart = [];
    renderCart();
  } catch (failure) {
    $('confirmation').hidden = true;
    error.textContent = `Checkout failed: ${failure.message}`;
    error.hidden = false;
    $('checkout').disabled = state.cart.length === 0;
  } finally {
    $('checkout').textContent = 'Checkout';
  }
}

$('checkout').addEventListener('click', checkout);
$('continue-shopping').addEventListener('click', () => { $('confirmation').hidden = true; });
renderCart();
loadProducts();
