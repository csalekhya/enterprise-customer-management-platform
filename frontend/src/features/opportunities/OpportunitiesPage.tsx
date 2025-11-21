import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, ChevronLeft, ChevronRight, Target } from 'lucide-react';
import clsx from 'clsx';
import { opportunitiesApi } from '@/api/opportunities';
import { describeError } from '@/api/client';
import { Badge, Card, EmptyState, ErrorState, PageHeader, Spinner } from '@/components/ui/Primitives';
import { useToast } from '@/components/ui/useToast';
import { RoleGate } from '@/components/layout/RoleGate';
import { formatCompactCurrency, formatCurrency, formatDate, humanise } from '@/hooks/useFormatters';
import type { Opportunity, OpportunityStage, PipelineBoard } from '@/types';

const STAGE_ORDER: OpportunityStage[] = ['DISCOVERY', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'];

const STAGE_ACCENT: Record<OpportunityStage, string> = {
  DISCOVERY: 'border-t-slate-300',
  PROPOSAL: 'border-t-brand-300',
  NEGOTIATION: 'border-t-brand-500',
  WON: 'border-t-emerald-500',
  LOST: 'border-t-rose-400',
};

function stageTotal(deals: Opportunity[]): number {
  return deals.reduce((sum, deal) => sum + deal.amount, 0);
}

/**
 * Kanban pipeline.
 *
 * Stage changes are driven by the arrow controls on each card rather than HTML5 drag events:
 * keyboard and screen-reader users get the same capability, and the mutation is a single
 * PATCH the server can validate. The board refetches on success, which also picks up the
 * probability the API pins when a deal is closed.
 */
export function OpportunitiesPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [moving, setMoving] = useState<number | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['opportunities', 'board'],
    queryFn: opportunitiesApi.board,
    staleTime: 15_000,
  });

  const mutation = useMutation({
    mutationFn: ({ id, stage }: { id: number; stage: OpportunityStage }) =>
      opportunitiesApi.changeStage(id, stage),
    onMutate: ({ id }) => setMoving(id),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['opportunities'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      toast.success(`${updated.name} moved to ${humanise(updated.stage)}`);
    },
    onError: (mutationError) => toast.error(describeError(mutationError, 'Could not move the deal')),
    onSettled: () => setMoving(null),
  });

  const board: PipelineBoard | undefined = data;

  const totals = useMemo(() => {
    if (!board) {
      return { open: 0, weighted: 0, won: 0 };
    }
    const openStages: OpportunityStage[] = ['DISCOVERY', 'PROPOSAL', 'NEGOTIATION'];
    const openDeals = openStages.flatMap((stage) => board[stage] ?? []);
    return {
      open: stageTotal(openDeals),
      weighted: openDeals.reduce((sum, deal) => sum + deal.weightedAmount, 0),
      won: stageTotal(board.WON ?? []),
    };
  }, [board]);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size={28} />
      </div>
    );
  }

  if (isError || !board) {
    return <ErrorState message={describeError(error, 'Could not load the pipeline')} onRetry={() => void refetch()} />;
  }

  const isEmpty = STAGE_ORDER.every((stage) => (board[stage] ?? []).length === 0);

  return (
    <>
      <PageHeader
        title="Pipeline"
        description={`${formatCurrency(totals.open)} open · ${formatCurrency(totals.weighted)} weighted · ${formatCurrency(totals.won)} won`}
      />

      {isEmpty ? (
        <Card>
          <EmptyState
            icon={<Target size={28} />}
            title="No opportunities yet"
            description="Deals created against an account appear here, grouped by stage."
          />
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
          {STAGE_ORDER.map((stage) => {
            const deals = board[stage] ?? [];
            const stageIndex = STAGE_ORDER.indexOf(stage);

            return (
              <section key={stage} className="flex min-w-0 flex-col">
                <div
                  className={clsx(
                    'rounded-t-xl border border-b-0 border-slate-200 border-t-4 bg-white px-3 py-2.5',
                    STAGE_ACCENT[stage],
                  )}
                >
                  <div className="flex items-baseline justify-between">
                    <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                      {humanise(stage)}
                    </h2>
                    <span className="text-[11px] text-slate-400">{deals.length}</span>
                  </div>
                  <p className="mt-0.5 text-sm font-semibold text-slate-900">
                    {formatCompactCurrency(stageTotal(deals))}
                  </p>
                </div>

                <div className="flex-1 space-y-2 rounded-b-xl border border-slate-200 bg-slate-100/60 p-2">
                  {deals.length === 0 ? (
                    <p className="px-1 py-6 text-center text-xs text-slate-400">No deals</p>
                  ) : (
                    deals.map((deal) => (
                      <article
                        key={deal.id}
                        className={clsx(
                          'rounded-lg border border-slate-200 bg-white p-3 shadow-sm transition-opacity',
                          moving === deal.id && 'opacity-50',
                        )}
                      >
                        <p className="text-sm font-medium leading-snug text-slate-900">{deal.name}</p>
                        <p className="mt-0.5 truncate text-xs text-slate-500">{deal.accountName}</p>

                        <div className="mt-2 flex items-center justify-between">
                          <span className="text-sm font-semibold text-slate-900">{formatCurrency(deal.amount)}</span>
                          <Badge tone={deal.probability >= 60 ? 'green' : 'neutral'}>{deal.probability}%</Badge>
                        </div>

                        <p className="mt-2 flex items-center gap-1 text-[11px] text-slate-500">
                          <CalendarClock size={12} />
                          {formatDate(deal.expectedCloseDate)}
                        </p>

                        <RoleGate roles={['ADMIN', 'SALES_MANAGER', 'SALES_REP']}>
                          <div className="mt-2 flex justify-between border-t border-slate-100 pt-2">
                            <button
                              type="button"
                              aria-label={`Move ${deal.name} to the previous stage`}
                              disabled={stageIndex === 0 || mutation.isPending}
                              onClick={() =>
                                mutation.mutate({ id: deal.id, stage: STAGE_ORDER[stageIndex - 1] })
                              }
                              className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30 disabled:hover:bg-transparent"
                            >
                              <ChevronLeft size={14} />
                            </button>
                            <button
                              type="button"
                              aria-label={`Move ${deal.name} to the next stage`}
                              disabled={stageIndex === STAGE_ORDER.length - 1 || mutation.isPending}
                              onClick={() =>
                                mutation.mutate({ id: deal.id, stage: STAGE_ORDER[stageIndex + 1] })
                              }
                              className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30 disabled:hover:bg-transparent"
                            >
                              <ChevronRight size={14} />
                            </button>
                          </div>
                        </RoleGate>
                      </article>
                    ))
                  )}
                </div>
              </section>
            );
          })}
        </div>
      )}
    </>
  );
}
