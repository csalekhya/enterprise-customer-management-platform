import { api, pageParams } from './client';
import type { Interaction, InteractionPayload, PageRequest, PageResponse } from '@/types';

export const interactionsApi = {
  async list(
    filters: { customerId?: number | ''; accountId?: number | '' },
    page: PageRequest,
  ): Promise<PageResponse<Interaction>> {
    const params: Record<string, string | number> = pageParams(page.page, page.size, page.sort);
    if (filters.customerId) params.customerId = filters.customerId;
    if (filters.accountId) params.accountId = filters.accountId;

    const { data } = await api.get<PageResponse<Interaction>>('/interactions', { params });
    return data;
  },

  async create(payload: InteractionPayload): Promise<Interaction> {
    const { data } = await api.post<Interaction>('/interactions', payload);
    return data;
  },

  async remove(id: number): Promise<void> {
    await api.delete(`/interactions/${id}`);
  },
};
