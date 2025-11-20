import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Search, Trash2, Users } from 'lucide-react';
import { customersApi } from '@/api/customers';
import { describeError } from '@/api/client';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
  Select,
} from '@/components/ui/Primitives';
import { Table, TableSkeleton, type Column } from '@/components/ui/Table';
import { Pagination } from '@/components/ui/Pagination';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/useToast';
import { RoleGate } from '@/components/layout/RoleGate';
import { useDebounce } from '@/hooks/useDebounce';
import { formatDate, humanise } from '@/hooks/useFormatters';
import { CustomerFormDrawer } from './CustomerFormDrawer';
import type { AccountTier, Customer, CustomerFilters, LifecycleStage } from '@/types';

const STAGE_TONE: Record<LifecycleStage, 'neutral' | 'blue' | 'green' | 'violet'> = {
  LEAD: 'neutral',
  QUALIFIED: 'blue',
  CUSTOMER: 'green',
  CHAMPION: 'violet',
};

const STAGES: LifecycleStage[] = ['LEAD', 'QUALIFIED', 'CUSTOMER', 'CHAMPION'];
const TIERS: AccountTier[] = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM'];

/**
 * Contact directory.
 *
 * Paging, sorting and filtering are all server-side. The query key carries every filter, so
 * React Query caches each distinct slice and going back to a previous page is instant while
 * the data stays authoritative.
 */
export function CustomersPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState('');
  const [stage, setStage] = useState<LifecycleStage | ''>('');
  const [tier, setTier] = useState<AccountTier | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState('lastName,asc');

  const [editing, setEditing] = useState<Customer | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Customer | null>(null);

  const debouncedSearch = useDebounce(search);

  const filters = useMemo<CustomerFilters>(
    () => ({ search: debouncedSearch, stage, tier }),
    [debouncedSearch, stage, tier],
  );

  const { data, isLoading, isFetching, isError, error, refetch } = useQuery({
    queryKey: ['customers', filters, page, size, sort],
    queryFn: () => customersApi.list(filters, { page, size, sort }),
    // Keeps the previous page on screen while the next one loads, so the table does not
    // collapse to a skeleton on every pagination click.
    placeholderData: (previous) => previous,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => customersApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      toast.success('Contact deleted');
      setPendingDelete(null);
    },
    onError: (mutationError) => {
      toast.error(describeError(mutationError, 'Could not delete the contact'));
      setPendingDelete(null);
    },
  });

  const resetToFirstPage = () => setPage(0);

  const columns: Array<Column<Customer>> = [
    {
      key: 'name',
      header: 'Contact',
      sortKey: 'lastName',
      render: (row) => (
        <div>
          <p className="font-medium text-slate-900">
            {row.firstName} {row.lastName}
          </p>
          <p className="text-xs text-slate-500">{row.email}</p>
        </div>
      ),
    },
    {
      key: 'account',
      header: 'Account',
      sortKey: 'account.name',
      render: (row) => (
        <div>
          <p className="text-slate-800">{row.accountName}</p>
          <p className="text-xs text-slate-500">{row.jobTitle ?? '—'}</p>
        </div>
      ),
    },
    {
      key: 'stage',
      header: 'Stage',
      sortKey: 'lifecycleStage',
      render: (row) => <Badge tone={STAGE_TONE[row.lifecycleStage]}>{humanise(row.lifecycleStage)}</Badge>,
    },
    { key: 'owner', header: 'Owner', render: (row) => <span className="text-slate-600">{row.ownerName}</span> },
    {
      key: 'tags',
      header: 'Tags',
      render: (row) =>
        row.tags ? (
          <div className="flex flex-wrap gap-1">
            {row.tags
              .split(',')
              .map((tag) => tag.trim())
              .filter(Boolean)
              .slice(0, 3)
              .map((tag) => (
                <span key={tag} className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">
                  {tag}
                </span>
              ))}
          </div>
        ) : (
          <span className="text-slate-400">—</span>
        ),
    },
    {
      key: 'created',
      header: 'Added',
      sortKey: 'createdAt',
      render: (row) => <span className="text-xs text-slate-500">{formatDate(row.createdAt)}</span>,
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      width: '6rem',
      render: (row) => (
        <RoleGate roles={['ADMIN', 'SALES_MANAGER', 'SALES_REP']}>
          <div className="flex justify-end gap-1">
            <button
              type="button"
              aria-label={`Edit ${row.firstName} ${row.lastName}`}
              onClick={() => {
                setEditing(row);
                setDrawerOpen(true);
              }}
              className="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
            >
              <Pencil size={15} />
            </button>
            <button
              type="button"
              aria-label={`Delete ${row.firstName} ${row.lastName}`}
              onClick={() => setPendingDelete(row)}
              className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
            >
              <Trash2 size={15} />
            </button>
          </div>
        </RoleGate>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Contacts"
        description="Every person across the book of business, filterable by stage and tier."
        action={
          <RoleGate roles={['ADMIN', 'SALES_MANAGER', 'SALES_REP']}>
            <Button
              icon={<Plus size={16} />}
              onClick={() => {
                setEditing(null);
                setDrawerOpen(true);
              }}
            >
              New contact
            </Button>
          </RoleGate>
        }
      />

      <Card>
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 px-4 py-3">
          <div className="relative min-w-[16rem] flex-1">
            <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <Input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                resetToFirstPage();
              }}
              placeholder="Search name or email…"
              aria-label="Search contacts"
              className="pl-9"
            />
          </div>

          <Select
            value={stage}
            aria-label="Filter by lifecycle stage"
            onChange={(event) => {
              setStage(event.target.value as LifecycleStage | '');
              resetToFirstPage();
            }}
            className="w-44"
          >
            <option value="">All stages</option>
            {STAGES.map((option) => (
              <option key={option} value={option}>
                {humanise(option)}
              </option>
            ))}
          </Select>

          <Select
            value={tier}
            aria-label="Filter by account tier"
            onChange={(event) => {
              setTier(event.target.value as AccountTier | '');
              resetToFirstPage();
            }}
            className="w-44"
          >
            <option value="">All tiers</option>
            {TIERS.map((option) => (
              <option key={option} value={option}>
                {humanise(option)}
              </option>
            ))}
          </Select>

          {isFetching && !isLoading ? <span className="text-xs text-slate-400">Updating…</span> : null}
        </div>

        {isLoading ? (
          <TableSkeleton columns={6} />
        ) : isError ? (
          <ErrorState message={describeError(error, 'Could not load contacts')} onRetry={() => void refetch()} />
        ) : (
          <>
            <Table
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(row) => row.id}
              sort={sort}
              onSortChange={(next) => {
                setSort(next);
                resetToFirstPage();
              }}
              emptyState={
                <EmptyState
                  icon={<Users size={28} />}
                  title="No contacts match these filters"
                  description="Try a broader search, or clear the stage and tier filters."
                  action={
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setSearch('');
                        setStage('');
                        setTier('');
                        resetToFirstPage();
                      }}
                    >
                      Clear filters
                    </Button>
                  }
                />
              }
            />

            {data && data.totalElements > 0 ? (
              <Pagination
                page={data.page}
                size={data.size}
                totalElements={data.totalElements}
                totalPages={data.totalPages}
                onPageChange={setPage}
                onSizeChange={(next) => {
                  setSize(next);
                  resetToFirstPage();
                }}
              />
            ) : null}
          </>
        )}
      </Card>

      <CustomerFormDrawer open={drawerOpen} customer={editing} onClose={() => setDrawerOpen(false)} />

      <Modal
        open={Boolean(pendingDelete)}
        title="Delete contact"
        description={
          pendingDelete
            ? `${pendingDelete.firstName} ${pendingDelete.lastName} and their activity trail will be removed. This cannot be undone.`
            : ''
        }
        onClose={() => setPendingDelete(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPendingDelete(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={deleteMutation.isPending}
              onClick={() => pendingDelete && deleteMutation.mutate(pendingDelete.id)}
            >
              Delete
            </Button>
          </>
        }
      />
    </>
  );
}
