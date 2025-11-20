import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Building2, ExternalLink, Search } from 'lucide-react';
import { accountsApi, type AccountFilters } from '@/api/accounts';
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
import { useDebounce } from '@/hooks/useDebounce';
import { formatCurrency, humanise } from '@/hooks/useFormatters';
import type { Account, AccountStatus, AccountTier } from '@/types';

const TIER_TONE: Record<AccountTier, 'neutral' | 'blue' | 'amber' | 'violet'> = {
  BRONZE: 'neutral',
  SILVER: 'blue',
  GOLD: 'amber',
  PLATINUM: 'violet',
};

const STATUS_TONE: Record<AccountStatus, 'green' | 'blue' | 'rose'> = {
  ACTIVE: 'green',
  PROSPECT: 'blue',
  CHURNED: 'rose',
};

const TIERS: AccountTier[] = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM'];
const STATUSES: AccountStatus[] = ['ACTIVE', 'PROSPECT', 'CHURNED'];

export function AccountsPage() {
  const [search, setSearch] = useState('');
  const [tier, setTier] = useState<AccountTier | ''>('');
  const [status, setStatus] = useState<AccountStatus | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState('name,asc');

  const debouncedSearch = useDebounce(search);
  const filters = useMemo<AccountFilters>(
    () => ({ search: debouncedSearch, tier, status }),
    [debouncedSearch, tier, status],
  );

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['accounts', filters, page, size, sort],
    queryFn: () => accountsApi.list(filters, { page, size, sort }),
    placeholderData: (previous) => previous,
  });

  const columns: Array<Column<Account>> = [
    {
      key: 'name',
      header: 'Account',
      sortKey: 'name',
      render: (row) => (
        <div>
          <p className="font-medium text-slate-900">{row.name}</p>
          <p className="text-xs text-slate-500">{humanise(row.industry)}</p>
        </div>
      ),
    },
    {
      key: 'tier',
      header: 'Tier',
      sortKey: 'tier',
      render: (row) => <Badge tone={TIER_TONE[row.tier]}>{humanise(row.tier)}</Badge>,
    },
    {
      key: 'status',
      header: 'Status',
      sortKey: 'status',
      render: (row) => <Badge tone={STATUS_TONE[row.status]}>{humanise(row.status)}</Badge>,
    },
    {
      key: 'revenue',
      header: 'Annual revenue',
      sortKey: 'annualRevenue',
      align: 'right',
      render: (row) => <span className="font-medium text-slate-800">{formatCurrency(row.annualRevenue)}</span>,
    },
    { key: 'owner', header: 'Owner', render: (row) => <span className="text-slate-600">{row.ownerName}</span> },
    {
      key: 'website',
      header: 'Website',
      render: (row) =>
        row.website ? (
          <a
            href={row.website}
            target="_blank"
            rel="noreferrer noopener"
            className="inline-flex items-center gap-1 text-xs font-medium text-brand-600 hover:underline"
          >
            Visit
            <ExternalLink size={12} />
          </a>
        ) : (
          <span className="text-slate-400">—</span>
        ),
    },
  ];

  return (
    <>
      <PageHeader title="Accounts" description="Companies in the book of business, with tier and revenue segment." />

      <Card>
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 px-4 py-3">
          <div className="relative min-w-[16rem] flex-1">
            <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <Input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Search name or website…"
              aria-label="Search accounts"
              className="pl-9"
            />
          </div>

          <Select
            value={tier}
            aria-label="Filter by tier"
            onChange={(event) => {
              setTier(event.target.value as AccountTier | '');
              setPage(0);
            }}
            className="w-40"
          >
            <option value="">All tiers</option>
            {TIERS.map((option) => (
              <option key={option} value={option}>
                {humanise(option)}
              </option>
            ))}
          </Select>

          <Select
            value={status}
            aria-label="Filter by status"
            onChange={(event) => {
              setStatus(event.target.value as AccountStatus | '');
              setPage(0);
            }}
            className="w-40"
          >
            <option value="">All statuses</option>
            {STATUSES.map((option) => (
              <option key={option} value={option}>
                {humanise(option)}
              </option>
            ))}
          </Select>
        </div>

        {isLoading ? (
          <TableSkeleton columns={6} />
        ) : isError ? (
          <ErrorState message={describeError(error, 'Could not load accounts')} onRetry={() => void refetch()} />
        ) : (
          <>
            <Table
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(row) => row.id}
              sort={sort}
              onSortChange={(next) => {
                setSort(next);
                setPage(0);
              }}
              emptyState={
                <EmptyState
                  icon={<Building2 size={28} />}
                  title="No accounts match these filters"
                  description="Widen the search or clear the tier and status filters."
                  action={
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setSearch('');
                        setTier('');
                        setStatus('');
                        setPage(0);
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
                  setPage(0);
                }}
              />
            ) : null}
          </>
        )}
      </Card>
    </>
  );
}
