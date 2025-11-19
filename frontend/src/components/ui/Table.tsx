import clsx from 'clsx';
import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
import type { ReactNode } from 'react';

export interface Column<T> {
  key: string;
  header: string;
  /** Property path the API sorts by; omit for a column the server cannot sort. */
  sortKey?: string;
  align?: 'left' | 'right';
  width?: string;
  render: (row: T) => ReactNode;
}

export interface TableProps<T> {
  columns: Array<Column<T>>;
  rows: T[];
  rowKey: (row: T) => string | number;
  /** Current sort, as Spring Data spells it: `property,direction`. */
  sort?: string;
  onSortChange?: (sort: string) => void;
  onRowClick?: (row: T) => void;
  emptyState?: ReactNode;
}

/**
 * Presentational table.
 *
 * Sorting is a server concern: the header emits the `property,direction` string the API
 * expects and the parent re-queries. Sorting the current page in the browser would silently
 * lie about the other pages.
 */
export function Table<T>({
  columns,
  rows,
  rowKey,
  sort,
  onSortChange,
  onRowClick,
  emptyState,
}: TableProps<T>) {
  const [sortProperty, sortDirection] = (sort ?? '').split(',');

  const toggleSort = (sortKey: string) => {
    if (!onSortChange) return;
    const nextDirection = sortProperty === sortKey && sortDirection === 'asc' ? 'desc' : 'asc';
    onSortChange(`${sortKey},${nextDirection}`);
  };

  if (rows.length === 0 && emptyState) {
    return <>{emptyState}</>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead>
          <tr className="bg-slate-50">
            {columns.map((column) => {
              const isSorted = column.sortKey && sortProperty === column.sortKey;
              return (
                <th
                  key={column.key}
                  scope="col"
                  style={column.width ? { width: column.width } : undefined}
                  className={clsx(
                    'px-4 py-3 text-xs font-semibold uppercase tracking-wide text-slate-500',
                    column.align === 'right' ? 'text-right' : 'text-left',
                  )}
                  aria-sort={isSorted ? (sortDirection === 'asc' ? 'ascending' : 'descending') : undefined}
                >
                  {column.sortKey && onSortChange ? (
                    <button
                      type="button"
                      onClick={() => toggleSort(column.sortKey as string)}
                      className={clsx(
                        'inline-flex items-center gap-1 hover:text-slate-800',
                        column.align === 'right' && 'flex-row-reverse',
                      )}
                    >
                      {column.header}
                      {isSorted ? (
                        sortDirection === 'asc' ? (
                          <ArrowUp size={12} />
                        ) : (
                          <ArrowDown size={12} />
                        )
                      ) : (
                        <ChevronsUpDown size={12} className="opacity-40" />
                      )}
                    </button>
                  ) : (
                    column.header
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={clsx('transition-colors', onRowClick && 'cursor-pointer hover:bg-slate-50')}
            >
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={clsx(
                    'whitespace-nowrap px-4 py-3 text-slate-700',
                    column.align === 'right' && 'text-right',
                  )}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Row placeholders that match the real row height, so the layout does not jump on load. */
export function TableSkeleton({ columns, rows = 6 }: { columns: number; rows?: number }) {
  return (
    <div className="divide-y divide-slate-100">
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div key={rowIndex} className="flex gap-4 px-4 py-3.5">
          {Array.from({ length: columns }).map((__, columnIndex) => (
            <div
              key={columnIndex}
              className="h-4 flex-1 animate-pulse rounded bg-slate-100"
              style={{ maxWidth: columnIndex === 0 ? '18rem' : '10rem' }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}
