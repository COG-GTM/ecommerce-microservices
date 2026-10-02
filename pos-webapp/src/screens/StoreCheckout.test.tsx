import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { lookupLineItem, placeOrder } from '../api/client';
import { MOCK_LINE_ITEMS } from '../api/fixtures';
import type { OrderResponse } from '../api/types';
import { StoreCheckout } from './StoreCheckout';

vi.mock('../api/client', () => ({
  lookupLineItem: vi.fn(),
  placeOrder: vi.fn(),
}));

describe('StoreCheckout charging', () => {
  beforeEach(() => {
    vi.mocked(lookupLineItem).mockReset();
    vi.mocked(placeOrder).mockReset();
  });

  it('submits only once while a charge is in flight', async () => {
    const user = userEvent.setup();
    let resolveOrder!: (response: OrderResponse) => void;
    vi.mocked(placeOrder).mockImplementation(
      () =>
        new Promise<OrderResponse>((resolve) => {
          resolveOrder = resolve;
        }),
    );
    render(<StoreCheckout />);

    const chargeButton = screen.getByRole('button', { name: /Charge/ });
    await user.dblClick(chargeButton);
    await user.click(chargeButton);

    expect(placeOrder).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Charging…' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Charging…' })).toHaveAttribute(
      'aria-busy',
      'true',
    );

    resolveOrder({ orderNumber: '1969-04-12345', status: 'COMPLETED', message: 'ok' });
    expect(await screen.findByRole('status')).toHaveTextContent(
      'COMPLETED · Order 1969-04-12345',
    );
    expect(screen.getByRole('button', { name: /Charge/ })).toBeDisabled();
  });

  it('resets the bag and promotions after success and sends an idempotency key', async () => {
    const user = userEvent.setup();
    vi.mocked(placeOrder).mockResolvedValue({
      orderNumber: '1969-04-12345',
      status: 'COMPLETED',
      message: 'ok',
    });
    render(<StoreCheckout />);

    await user.click(screen.getByRole('button', { name: /Charge/ }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'COMPLETED · Order 1969-04-12345',
    );
    expect(screen.getByText('Scan a hangtag to start the transaction.')).toBeInTheDocument();
    expect(screen.getByText('0 items')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Charge/ })).toBeDisabled();
    expect(screen.queryByText(/FALL30/)).not.toBeInTheDocument();
    expect(screen.getByText('None applied')).toBeInTheDocument();

    const request = vi.mocked(placeOrder).mock.calls[0][0];
    expect(typeof request.idempotencyKey).toBe('string');
    expect(request.idempotencyKey.length).toBeGreaterThan(0);
    expect(request.lineItems).toEqual(
      MOCK_LINE_ITEMS.map(({ skuCode, quantity }) => ({ skuCode, quantity })),
    );
  });

  it('keeps the bag after failure and reuses the idempotency key on retry', async () => {
    const user = userEvent.setup();
    vi.mocked(placeOrder)
      .mockRejectedValueOnce(new Error('POST /api/order failed: 503'))
      .mockResolvedValueOnce({
        orderNumber: '1969-04-12345',
        status: 'COMPLETED',
        message: 'ok',
      });
    render(<StoreCheckout />);

    await user.click(screen.getByRole('button', { name: /Charge/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Charge failed: POST /api/order failed: 503. Bag kept — try again.',
    );
    expect(screen.getByText('Vintage Soft Crewneck Tee')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Charge/ })).toBeEnabled();
    const firstKey = vi.mocked(placeOrder).mock.calls[0][0].idempotencyKey;

    await user.click(screen.getByRole('button', { name: /Charge/ }));

    await waitFor(() => expect(placeOrder).toHaveBeenCalledTimes(2));
    expect(vi.mocked(placeOrder).mock.calls[1][0].idempotencyKey).toBe(firstKey);
    expect(await screen.findByRole('status')).toHaveTextContent(
      'COMPLETED · Order 1969-04-12345',
    );
  });

  it('uses a new idempotency key for the next transaction after success', async () => {
    const user = userEvent.setup();
    vi.mocked(placeOrder).mockResolvedValue({
      orderNumber: '1969-04-12345',
      status: 'COMPLETED',
      message: 'ok',
    });
    vi.mocked(lookupLineItem).mockResolvedValue(MOCK_LINE_ITEMS[0]);
    render(<StoreCheckout />);

    await user.click(screen.getByRole('button', { name: /Charge/ }));
    await screen.findByRole('status');
    const firstKey = vi.mocked(placeOrder).mock.calls[0][0].idempotencyKey;

    await user.type(screen.getByLabelText(/Look up — scan hangtag/), MOCK_LINE_ITEMS[0].skuCode);
    await user.click(screen.getByRole('button', { name: 'Add to bag' }));
    await user.click(screen.getByRole('button', { name: /Charge/ }));

    await waitFor(() => expect(placeOrder).toHaveBeenCalledTimes(2));
    expect(vi.mocked(placeOrder).mock.calls[1][0].idempotencyKey).not.toBe(firstKey);
  });
});
