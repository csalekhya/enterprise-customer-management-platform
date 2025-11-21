import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Mail, MessagesSquare, Phone, StickyNote, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { interactionsApi } from '@/api/interactions';
import { describeError } from '@/api/client';
import { Badge, Card, EmptyState, ErrorState, PageHeader, Spinner } from '@/components/ui/Primitives';
import { Pagination } from '@/components/ui/Pagination';
import { formatDateTime, formatRelative, humanise } from '@/hooks/useFormatters';
import type { InteractionType } from '@/types';

const TYPE_ICON: Record<InteractionType, ReactNode> = {
  CALL: <Phone size={14} />,
  EMAIL: <Mail size={14} />,
  MEETING: <Users size={14} />,
  NOTE: <StickyNote size={14} />,
};

const TYPE_TONE: Record<InteractionType, 'blue' | 'violet' | 'green' | 'neutral'> = {
  CALL: 'blue',
  EMAIL: 'violet',
  MEETING: 'green',
  NOTE: 'neutral',
};

/**
 * Cross-account activity timeline.
 *
 * Rendered as a vertical timeline rather than a table: the value of an audit trail is the
 * sequence, and a reader scans it by recency, not by column.
 */
export function InteractionsPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['interactions', page, size],
    queryFn: () => interactionsApi.list({}, { page, size }),
    placeholderData: (previous) => previous,
  });

  return (
    <>
      <PageHeader
        title="Activity"
        description="Every call, email, meeting and note logged against a contact, newest first."
      />

      <Card>
        {isLoading ? (
          <div className="flex h-56 items-center justify-center">
            <Spinner size={26} />
          </div>
        ) : isError ? (
          <ErrorState message={describeError(error, 'Could not load the activity feed')} onRetry={() => void refetch()} />
        ) : !data || data.content.length === 0 ? (
          <EmptyState
            icon={<MessagesSquare size={28} />}
            title="No activity recorded"
            description="Interactions logged against a contact appear here as a timeline."
          />
        ) : (
          <>
            <ol className="divide-y divide-slate-100">
              {data.content.map((interaction) => (
                <li key={interaction.id} className="flex gap-4 px-5 py-4">
                  <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-600">
                    {TYPE_ICON[interaction.type]}
                  </span>

                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-medium text-slate-900">{interaction.subject}</p>
                      <Badge tone={TYPE_TONE[interaction.type]}>{humanise(interaction.type)}</Badge>
                    </div>

                    <p className="mt-0.5 text-xs text-slate-500">
                      {interaction.customerName} · {interaction.accountName} · logged by {interaction.authorName}
                    </p>

                    {interaction.body ? (
                      <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-slate-600">
                        {interaction.body}
                      </p>
                    ) : null}
                  </div>

                  <div className="shrink-0 text-right">
                    <p className="text-xs font-medium text-slate-600">{formatRelative(interaction.occurredAt)}</p>
                    <p className="mt-0.5 text-[11px] text-slate-400">{formatDateTime(interaction.occurredAt)}</p>
                  </div>
                </li>
              ))}
            </ol>

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
          </>
        )}
      </Card>
    </>
  );
}
