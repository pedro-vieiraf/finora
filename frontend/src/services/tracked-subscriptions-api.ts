import { apiRequest } from './api';

const PATH = '/open-finance/subscriptions';

export type SubscriptionCharge =
  | { source: 'BANK'; transactionId: string }
  | { source: 'CARD'; cardId: string; chargeId: string };

export interface TrackedSubscription {
  id: string;
  description: string;
  source: 'BANK' | 'CARD';
  accountLabel: string;
  currency: string;
  amount: number;
  lastChargeDate: string;
  nextChargeDate: string;
  lastChargeId: string | null;
  sourceAvailable: boolean;
}

export function getTrackedSubscriptions() {
  return apiRequest<TrackedSubscription[]>(PATH);
}

export function trackSubscription(charge: SubscriptionCharge) {
  const route = charge.source === 'BANK'
    ? `bank/${encodeURIComponent(charge.transactionId)}`
    : `cards/${encodeURIComponent(charge.cardId)}/charges/${encodeURIComponent(charge.chargeId)}`;
  return apiRequest<void>(`${PATH}/${route}`, { method: 'PUT' });
}

export function removeTrackedSubscription(id: string) {
  return apiRequest<void>(`${PATH}/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
