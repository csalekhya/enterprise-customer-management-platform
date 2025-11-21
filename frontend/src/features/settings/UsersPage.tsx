import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ShieldCheck, UserCog } from 'lucide-react';
import { usersApi } from '@/api/users';
import { describeError } from '@/api/client';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
} from '@/components/ui/Primitives';
import { Table, TableSkeleton, type Column } from '@/components/ui/Table';
import { Pagination } from '@/components/ui/Pagination';
import { useToast } from '@/components/ui/useToast';
import { useDebounce } from '@/hooks/useDebounce';
import { formatDate, formatRelative, humanise } from '@/hooks/useFormatters';
import type { RoleName, User } from '@/types';

const ROLE_TONE: Record<RoleName, 'rose' | 'violet' | 'blue' | 'neutral'> = {
  ADMIN: 'rose',
  SALES_MANAGER: 'violet',
  SALES_REP: 'blue',
  READ_ONLY: 'neutral',
};

/**
 * User administration, reachable only by ADMIN.
 *
 * The route is guarded, the nav item is hidden and the API refuses the call - three
 * independent layers, of which only the last one actually matters.
 */
export function UsersPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const debouncedSearch = useDebounce(search);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['users', debouncedSearch, page, size],
    queryFn: () => usersApi.list(debouncedSearch, { page, size, sort: 'lastName,asc' }),
    placeholderData: (previous) => previous,
  });

  const toggleEnabled = useMutation({
    mutationFn: (user: User) =>
      usersApi.update(user.id, {
        firstName: user.firstName,
        lastName: user.lastName,
        enabled: !user.enabled,
        roles: user.roles,
      }),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success(`${updated.email} ${updated.enabled ? 'enabled' : 'disabled'}`);
    },
    onError: (mutationError) => toast.error(describeError(mutationError, 'Could not update the user')),
  });

  const columns: Array<Column<User>> = [
    {
      key: 'user',
      header: 'User',
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
      key: 'roles',
      header: 'Roles',
      render: (row) => (
        <div className="flex flex-wrap gap-1">
          {row.roles.map((role) => (
            <Badge key={role} tone={ROLE_TONE[role]}>
              {humanise(role)}
            </Badge>
          ))}
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => <Badge tone={row.enabled ? 'green' : 'rose'}>{row.enabled ? 'Enabled' : 'Disabled'}</Badge>,
    },
    {
      key: 'lastLogin',
      header: 'Last login',
      render: (row) => (
        <span className="text-xs text-slate-500">
          {row.lastLoginAt ? formatRelative(row.lastLoginAt) : 'Never'}
        </span>
      ),
    },
    {
      key: 'created',
      header: 'Created',
      sortKey: 'createdAt',
      render: (row) => <span className="text-xs text-slate-500">{formatDate(row.createdAt)}</span>,
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (row) => (
        <Button
          size="sm"
          variant="secondary"
          loading={toggleEnabled.isPending && toggleEnabled.variables?.id === row.id}
          onClick={() => toggleEnabled.mutate(row)}
        >
          {row.enabled ? 'Disable' : 'Enable'}
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="User administration"
        description="Assign roles and disable access. Changing a role revokes the user's refresh tokens."
      />

      <div className="mb-4 flex items-start gap-3 rounded-lg border border-brand-200 bg-brand-50 px-4 py-3">
        <ShieldCheck size={16} className="mt-0.5 shrink-0 text-brand-600" />
        <p className="text-xs leading-relaxed text-brand-900">
          Authorities are embedded in issued access tokens. A role change therefore revokes the
          user&apos;s refresh chain so their next silent refresh fails and they re-authenticate with
          the new permissions, rather than keeping the old ones for up to 15 minutes.
        </p>
      </div>

      <Card>
        <div className="border-b border-slate-200 px-4 py-3">
          <Input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Search by name or email…"
            aria-label="Search users"
            className="max-w-sm"
          />
        </div>

        {isLoading ? (
          <TableSkeleton columns={6} />
        ) : isError ? (
          <ErrorState message={describeError(error, 'Could not load users')} onRetry={() => void refetch()} />
        ) : (
          <>
            <Table
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(row) => row.id}
              emptyState={
                <EmptyState icon={<UserCog size={28} />} title="No users match that search" />
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
