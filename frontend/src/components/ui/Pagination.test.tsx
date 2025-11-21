import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pagination } from './Pagination';
import { pageWindow } from './pageWindow';

describe('pageWindow', () => {
  it('lists every page when there are only a few', () => {
    expect(pageWindow(0, 3)).toEqual([0, 1, 2]);
  });

  it('collapses the middle of a long range with a single gap on each side', () => {
    expect(pageWindow(10, 40)).toEqual([0, 'gap', 9, 10, 11, 'gap', 39]);
  });

  it('never emits a gap that hides exactly one page', () => {
    // Pages 0..3 with the cursor on 2: page 1 is adjacent, so an ellipsis would be a lie.
    expect(pageWindow(2, 4)).toEqual([0, 1, 2, 3]);
  });

  it('returns nothing when there is no data', () => {
    expect(pageWindow(0, 0)).toEqual([]);
  });
});

describe('<Pagination />', () => {
  const baseProps = {
    page: 0,
    size: 20,
    totalElements: 95,
    totalPages: 5,
    onPageChange: vi.fn(),
  };

  const summaryText = () => screen.getByText(/Showing/).textContent?.replace(/\s+/g, ' ') ?? '';

  it('reports the one-based range of the current page', () => {
    render(<Pagination {...baseProps} />);

    expect(summaryText()).toContain('1–20 of 95');
  });

  it('reports the range of a middle page, clamped to the total', () => {
    render(<Pagination {...baseProps} page={4} />);

    expect(summaryText()).toContain('81–95 of 95');
  });

  it('disables the previous control on the first page', () => {
    render(<Pagination {...baseProps} />);

    expect(screen.getByLabelText('Previous page')).toBeDisabled();
    expect(screen.getByLabelText('Next page')).toBeEnabled();
  });

  it('disables the next control on the last page', () => {
    render(<Pagination {...baseProps} page={4} />);

    expect(screen.getByLabelText('Next page')).toBeDisabled();
  });

  it('emits the zero-based index of the clicked page', async () => {
    const onPageChange = vi.fn();
    // On page 2 of 5 the window is 1 2 3 … 5, so the "3" control is rendered. From page 1
    // it would sit behind the ellipsis (1 2 … 5).
    render(<Pagination {...baseProps} page={1} onPageChange={onPageChange} />);

    await userEvent.click(screen.getByRole('button', { name: '3' }));

    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('marks the active page for assistive technology', () => {
    render(<Pagination {...baseProps} page={2} />);

    expect(screen.getByRole('button', { name: '3' })).toHaveAttribute('aria-current', 'page');
  });

  it('reports an empty result set as 0 rather than 1-0', () => {
    render(<Pagination {...baseProps} totalElements={0} totalPages={0} />);

    expect(summaryText()).toContain('0–0 of 0');
  });

  it('emits the new page size when the selector changes', async () => {
    const onSizeChange = vi.fn();
    render(<Pagination {...baseProps} onSizeChange={onSizeChange} />);

    await userEvent.selectOptions(screen.getByLabelText('Rows per page'), '50');

    expect(onSizeChange).toHaveBeenCalledWith(50);
  });
});
