import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Activity, Building2, TrendingUp, UserPlus } from 'lucide-react';
import type { ReactNode } from 'react';
import { dashboardApi } from '@/api/dashboard';
import { describeError } from '@/api/client';
import { Badge, Card, CardHeader, ErrorState, PageHeader, Spinner } from '@/components/ui/Primitives';
import {
  formatCompactCurrency,
  formatCurrency,
  formatNumber,
  formatRelative,
  humanise,
} from '@/hooks/useFormatters';
import type { AccountTier } from '@/types';

const STAGE_COLOURS = ['#8eb0ff', '#5985ff', '#325dff', '#059669', '#e11d48'];
const FUNNEL_COLOURS = ['#cbd5e1', '#93c5fd', '#3b82f6', '#1d4ed8'];

const TIER_TONE: Record<AccountTier, 'neutral' | 'blue' | 'amber' | 'violet'> = {
  BRONZE: 'neutral',
  SILVER: 'blue',
  GOLD: 'amber',
  PLATINUM: 'violet',
};

function KpiCard({
  label,
  value,
  caption,
  icon,
}: {
  label: string;
  value: string;
  caption?: string;
  icon: ReactNode;
}) {
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
        <span className="rounded-lg bg-brand-50 p-1.5 text-brand-600">{icon}</span>
      </div>
      <p className="mt-3 text-2xl font-semibold tracking-tight text-slate-900">{value}</p>
      {caption ? <p className="mt-1 text-xs text-slate-500">{caption}</p> : null}
    </Card>
  );
}

/**
 * Landing screen.
 *
 * Everything on it comes from one request. The endpoint is cached for 60 s server-side and
 * React Query keeps it fresh for 30 s client-side, so a user moving between screens does not
 * re-fetch the aggregates on every return.
 */
export function DashboardPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: dashboardApi.summary,
    staleTime: 30_000,
  });

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size={28} />
      </div>
    );
  }

  if (isError || !data) {
    return <ErrorState message={describeError(error, 'Could not load the dashboard')} onRetry={() => void refetch()} />;
  }

  const pipelineData = data.pipelineByStage.map((entry) => ({
    stage: humanise(entry.stage),
    amount: entry.amount,
    weighted: entry.weightedAmount,
    deals: entry.dealCount,
  }));

  const funnelData = data.contactFunnel.map((entry) => ({
    name: humanise(entry.stage),
    value: entry.contactCount,
  }));

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={`Pipeline and activity overview · refreshed ${formatRelative(data.generatedAt)}`}
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          label="Open pipeline"
          value={formatCurrency(data.openPipelineValue)}
          caption={`${formatCurrency(data.weightedPipelineValue)} weighted by probability`}
          icon={<TrendingUp size={16} />}
        />
        <KpiCard
          label="Accounts"
          value={formatNumber(data.totalAccounts)}
          caption={`${data.activeAccounts} active · ${data.prospectAccounts} prospects`}
          icon={<Building2 size={16} />}
        />
        <KpiCard
          label="New contacts"
          value={formatNumber(data.newContactsThisMonth)}
          caption={`this month · ${formatNumber(data.totalContacts)} in total`}
          icon={<UserPlus size={16} />}
        />
        <KpiCard
          label="Activity"
          value={formatNumber(data.interactionsLast30Days)}
          caption="interactions in the last 30 days"
          icon={<Activity size={16} />}
        />
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Pipeline by stage" subtitle="Total value against probability-weighted value" />
          <div className="h-72 px-3 py-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={pipelineData} margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                <XAxis dataKey="stage" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis
                  tickFormatter={(value: number) => formatCompactCurrency(value)}
                  tick={{ fontSize: 12, fill: '#64748b' }}
                  axisLine={false}
                  tickLine={false}
                  width={70}
                />
                <Tooltip
                  formatter={(value: number, name: string) => [formatCurrency(value), humanise(name)]}
                  contentStyle={{ borderRadius: 10, border: '1px solid #e2e8f0', fontSize: 12 }}
                />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="amount" name="Total" radius={[6, 6, 0, 0]} fill="#325dff" />
                <Bar dataKey="weighted" name="Weighted" radius={[6, 6, 0, 0]} fill="#8eb0ff" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card>
          <CardHeader title="Contact funnel" subtitle="Contacts by lifecycle stage" />
          <div className="h-72 px-3 py-4">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={funnelData} dataKey="value" nameKey="name" innerRadius={52} outerRadius={82} paddingAngle={2}>
                  {funnelData.map((entry, index) => (
                    <Cell key={entry.name} fill={FUNNEL_COLOURS[index % FUNNEL_COLOURS.length]} />
                  ))}
                </Pie>
                <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid #e2e8f0', fontSize: 12 }} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Top accounts by revenue" subtitle="Excluding churned accounts" />
          <ul className="divide-y divide-slate-100">
            {data.topAccountsByRevenue.map((account, index) => (
              <li key={account.id} className="flex items-center justify-between gap-4 px-5 py-3">
                <div className="flex min-w-0 items-center gap-3">
                  <span className="w-5 shrink-0 text-xs font-semibold text-slate-400">{index + 1}</span>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-800">{account.name}</p>
                    <p className="text-xs text-slate-500">{humanise(account.tier)} tier</p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <Badge tone={TIER_TONE[account.tier]}>{humanise(account.tier)}</Badge>
                  <span className="text-sm font-semibold text-slate-900">
                    {formatCurrency(account.annualRevenue)}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <CardHeader title="Activity mix" subtitle="Last 30 days by channel" />
          <div className="space-y-3 px-5 py-4">
            {data.activityMix.length === 0 ? (
              <p className="text-sm text-slate-500">No activity logged in the last 30 days.</p>
            ) : (
              data.activityMix.map((entry, index) => {
                const max = Math.max(...data.activityMix.map((item) => item.total));
                const width = max === 0 ? 0 : Math.round((entry.total / max) * 100);
                return (
                  <div key={entry.type}>
                    <div className="mb-1 flex items-center justify-between text-xs">
                      <span className="font-medium text-slate-700">{humanise(entry.type)}</span>
                      <span className="text-slate-500">{formatNumber(entry.total)}</span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full"
                        style={{
                          width: `${width}%`,
                          backgroundColor: STAGE_COLOURS[index % STAGE_COLOURS.length],
                        }}
                      />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </Card>
      </div>
    </>
  );
}
