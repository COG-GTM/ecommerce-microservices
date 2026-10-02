import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MOCK_LINE_ITEMS } from '../api/fixtures';
import indexCss from '../index.css?raw';
import { CartLineItem } from './CartLineItem';
import cartLineItemCss from './CartLineItem.module.css?raw';

function renderCart(onRemove = vi.fn()) {
  render(
    <>
      {MOCK_LINE_ITEMS.map((item) => (
        <CartLineItem key={item.skuCode} item={item} onRemove={onRemove} />
      ))}
    </>,
  );
  return onRemove;
}

function cssToken(name: string): string {
  const match = indexCss.match(new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`));
  if (!match) throw new Error(`Missing CSS token --${name}`);
  return match[1];
}

function moduleColorToken(className: string): string {
  const match = cartLineItemCss.match(
    new RegExp(`\\.${className}\\s*\\{[^}]*color:\\s*var\\(--([\\w-]+)\\)`),
  );
  if (!match) throw new Error(`Missing color for .${className}`);
  return match[1];
}

function relativeLuminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const channel = parseInt(hex.slice(i, i + 2), 16) / 255;
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrastRatio(foreground: string, background: string): number {
  const [light, dark] = [relativeLuminance(foreground), relativeLuminance(background)].sort(
    (a, b) => b - a,
  );
  return (light + 0.05) / (dark + 0.05);
}

describe('CartLineItem', () => {
  it('gives each Void line button an item-specific accessible name', () => {
    renderCart();

    const buttons = screen.getAllByRole('button', { name: /^Void line/ });
    expect(buttons).toHaveLength(MOCK_LINE_ITEMS.length);
    expect(new Set(buttons.map((button) => button.getAttribute('aria-label'))).size).toBe(
      MOCK_LINE_ITEMS.length,
    );
    expect(
      screen.getByRole('button', { name: 'Void line: Vintage Soft Crewneck Tee, size L' }),
    ).toHaveTextContent('Void line');
    expect(
      screen.getByRole('button', { name: 'Void line: High Rise Straight Jean, size 29 Reg' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Void line: Logo Fleece Hoodie, size M' }),
    ).toBeInTheDocument();
  });

  it('voids the SKU of the button activated by its accessible name', async () => {
    const onRemove = renderCart();

    await userEvent.click(
      screen.getByRole('button', { name: 'Void line: High Rise Straight Jean, size 29 Reg' }),
    );

    expect(onRemove).toHaveBeenCalledTimes(1);
    expect(onRemove).toHaveBeenCalledWith('471902-004-29');
  });

  it('hides the decorative color swatch and keeps the color name in text', () => {
    const [item] = MOCK_LINE_ITEMS;
    const { container } = render(<CartLineItem item={item} onRemove={vi.fn()} />);

    expect(container.querySelector('[aria-label]:not(button)')).toBeNull();
    const swatch = container.querySelector('[aria-hidden="true"]');
    expect(swatch).not.toBeNull();
    expect(swatch).toHaveStyle({ background: item.colorCode });
    expect(screen.getByText(`${item.colorName} · Size ${item.size}`)).toBeInTheDocument();
  });

  it('meets WCAG AA 4.5:1 contrast for ship-from-store eligible and ineligible text', () => {
    const surface = cssToken('surface');

    for (const className of ['eligible', 'ineligible']) {
      const foreground = cssToken(moduleColorToken(className));
      expect(contrastRatio(foreground, surface)).toBeGreaterThanOrEqual(4.5);
    }
  });
});
