import { api, pageParams } from './client';
import type {
  Opportunity,
  OpportunityPayload,
  OpportunityStage,
  PageRequest,
  PageResponse,
  PipelineBoard,
} from '@/types';

export const opportunitiesApi = {
  async list(
    filters: { stage?: OpportunityStage | ''; accountId?: number | '' },
    page: PageRequest,
  ): Promise<PageResponse<Opportunity>> {
    const params: Record<string, string | number> = pageParams(page.page, page.size, page.sort);
    if (filters.stage) params.stage = filters.stage;
    if (filters.accountId) params.accountId = filters.accountId;

    const { data } = await api.get<PageResponse<Opportunity>>('/opportunities', { params });
    return data;
  },

  /** One request returns every column of the kanban board, empty stages included. */
  async board(): Promise<PipelineBoard> {
    const { data } = await api.get<PipelineBoard>('/opportunities/board');
    return data;
  },

  async create(payload: OpportunityPayload): Promise<Opportunity> {
    const { data } = await api.post<Opportunity>('/opportunities', payload);
    return data;
  },

  async update(id: number, payload: OpportunityPayload): Promise<Opportunity> {
    const { data } = await api.put<Opportunity>(`/opportunities/${id}`, payload);
    return data;
  },

  async changeStage(id: number, stage: OpportunityStage, probability?: number): Promise<Opportunity> {
    const { data } = await api.patch<Opportunity>(`/opportunities/${id}/stage`, { stage, probability });
    return data;
  },

  async remove(id: number): Promise<void> {
    await api.delete(`/opportunities/${id}`);
  },
};
