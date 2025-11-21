/**
 * Shapes returned by the API.
 *
 * <p>Hand-written rather than generated: the file is small, it documents the contract in the
 * language the UI speaks, and every field here is one the front end actually renders.
 */

export type RoleName = 'ADMIN' | 'SALES_MANAGER' | 'SALES_REP' | 'READ_ONLY';

export type AccountTier = 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM';
export type AccountStatus = 'ACTIVE' | 'PROSPECT' | 'CHURNED';
export type Industry =
  | 'TECHNOLOGY'
  | 'FINANCIAL_SERVICES'
  | 'HEALTHCARE'
  | 'MANUFACTURING'
  | 'RETAIL'
  | 'LOGISTICS'
  | 'ENERGY'
  | 'PUBLIC_SECTOR'
  | 'EDUCATION'
  | 'OTHER';

export type LifecycleStage = 'LEAD' | 'QUALIFIED' | 'CUSTOMER' | 'CHAMPION';
export type InteractionType = 'CALL' | 'EMAIL' | 'MEETING' | 'NOTE';
export type OpportunityStage = 'DISCOVERY' | 'PROPOSAL' | 'NEGOTIATION' | 'WON' | 'LOST';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** RFC 7807 problem document, the single error shape the API returns. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  errors?: Record<string, string>;
}

export interface AuthenticatedUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  roles: RoleName[];
  lastLoginAt: string | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthenticatedUser;
}

export interface Account {
  id: number;
  name: string;
  industry: Industry;
  tier: AccountTier;
  annualRevenue: number | null;
  website: string | null;
  status: AccountStatus;
  ownerUserId: number;
  ownerName: string;
  contactCount: number;
  openOpportunityCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AccountPayload {
  name: string;
  industry: Industry;
  tier: AccountTier;
  annualRevenue: number | null;
  website: string | null;
  status: AccountStatus;
  ownerUserId: number | null;
}

export interface Customer {
  id: number;
  accountId: number;
  accountName: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  jobTitle: string | null;
  lifecycleStage: LifecycleStage;
  ownerUserId: number;
  ownerName: string;
  tags: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerPayload {
  accountId: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  jobTitle: string | null;
  lifecycleStage: LifecycleStage;
  ownerUserId: number | null;
  tags: string | null;
}

export interface CustomerFilters {
  search?: string;
  stage?: LifecycleStage | '';
  accountId?: number | '';
  ownerUserId?: number | '';
  tag?: string;
  tier?: AccountTier | '';
}

export interface Interaction {
  id: number;
  customerId: number;
  customerName: string;
  accountId: number;
  accountName: string;
  type: InteractionType;
  subject: string;
  body: string | null;
  occurredAt: string;
  authorId: number;
  authorName: string;
  createdAt: string;
}

export interface InteractionPayload {
  customerId: number;
  type: InteractionType;
  subject: string;
  body: string | null;
  occurredAt: string;
}

export interface Opportunity {
  id: number;
  accountId: number;
  accountName: string;
  name: string;
  amount: number;
  weightedAmount: number;
  stage: OpportunityStage;
  probability: number;
  expectedCloseDate: string | null;
  ownerUserId: number;
  ownerName: string;
  createdAt: string;
  updatedAt: string;
}

export interface OpportunityPayload {
  accountId: number;
  name: string;
  amount: number;
  stage: OpportunityStage;
  probability: number;
  expectedCloseDate: string | null;
  ownerUserId: number | null;
}

export type PipelineBoard = Record<OpportunityStage, Opportunity[]>;

export interface DashboardSummary {
  totalAccounts: number;
  activeAccounts: number;
  prospectAccounts: number;
  totalContacts: number;
  newContactsThisMonth: number;
  interactionsLast30Days: number;
  openPipelineValue: number;
  weightedPipelineValue: number;
  wonValueAllTime: number;
  pipelineByStage: Array<{
    stage: OpportunityStage;
    amount: number;
    weightedAmount: number;
    dealCount: number;
  }>;
  contactFunnel: Array<{ stage: LifecycleStage; contactCount: number }>;
  activityMix: Array<{ type: InteractionType; total: number }>;
  topAccountsByRevenue: Array<{
    id: number;
    name: string;
    tier: AccountTier;
    annualRevenue: number;
  }>;
  generatedAt: string;
}

export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: RoleName[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface UserOption {
  id: number;
  name: string;
  email: string;
}

export interface PageRequest {
  page: number;
  size: number;
  sort?: string;
}
