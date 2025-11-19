import { api } from './client';
import type { DashboardSummary } from '@/types';

export const dashboardApi = {
  /** One request backs every KPI card and chart on the landing screen. */
  async summary(): Promise<DashboardSummary> {
    const { data } = await api.get<DashboardSummary>('/dashboard/summary');
    return data;
  },
};
