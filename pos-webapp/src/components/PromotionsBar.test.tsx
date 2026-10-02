import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PromotionsBar } from './PromotionsBar';

function renderBar(onApply = vi.fn().mockReturnValue('applied')) {
  render(
    <PromotionsBar promotions={[]} onApply={onApply} onRemove={vi.fn()} />,
  );
  return onApply;
}

async function applyCode(value: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Promo code'), value);
  await user.click(screen.getByRole('button', { name: 'Apply' }));
  return user;
}

describe('PromotionsBar', () => {
  it('applies a trimmed, uppercased code and clears the input', async () => {
    const onApply = renderBar();
    await applyCode(' fall30 ');

    expect(onApply).toHaveBeenCalledWith('FALL30');
    expect(screen.getByRole('status')).toHaveTextContent('FALL30 applied.');
    expect(screen.getByLabelText('Promo code')).toHaveValue('');
  });

  it('shows an error for an unknown code and keeps the input', async () => {
    const onApply = vi.fn().mockReturnValue('unknown');
    renderBar(onApply);
    await applyCode('bogus');

    const input = screen.getByLabelText('Promo code');
    expect(onApply).toHaveBeenCalledWith('BOGUS');
    expect(screen.getByRole('status')).toHaveTextContent(
      'BOGUS is not a valid promo code.',
    );
    expect(input).toHaveValue('bogus');
    expect(input).toHaveAttribute('aria-invalid', 'true');
  });

  it('shows an error for a duplicate code and keeps the input', async () => {
    const onApply = vi.fn().mockReturnValue('duplicate');
    renderBar(onApply);
    await applyCode('fall30');

    const input = screen.getByLabelText('Promo code');
    expect(screen.getByRole('status')).toHaveTextContent(
      'FALL30 is already applied.',
    );
    expect(input).toHaveValue('fall30');
    expect(input).toHaveAttribute('aria-invalid', 'true');
  });

  it('clears the feedback message when typing resumes', async () => {
    const onApply = vi.fn().mockReturnValue('unknown');
    renderBar(onApply);
    const user = await applyCode('bogus');
    expect(screen.getByRole('status')).toHaveTextContent(
      'BOGUS is not a valid promo code.',
    );

    await user.type(screen.getByLabelText('Promo code'), 'x');
    expect(screen.getByRole('status')).toHaveTextContent('');
  });

  it('clears the feedback message when a promotion is removed', async () => {
    const onApply = vi.fn().mockReturnValue('duplicate');
    const onRemove = vi.fn();
    render(
      <PromotionsBar
        promotions={[{ code: 'FALL30', description: '30% off', percentOff: 30, amountOff: 0 }]}
        onApply={onApply}
        onRemove={onRemove}
      />,
    );
    const user = await applyCode('fall30');
    expect(screen.getByRole('status')).toHaveTextContent('FALL30 is already applied.');

    await user.click(screen.getByRole('button', { name: 'Remove FALL30' }));
    expect(onRemove).toHaveBeenCalledWith('FALL30');
    expect(screen.getByRole('status')).toHaveTextContent('');
  });
});
