import type { OpenFinanceCard } from '../services/open-finance-card-bills-api';

export function describeNextConnectedBill(card: OpenFinanceCard) {
  const month = card.nextCycle ?? card.nextDueDate?.slice(0, 7) ?? null;
  return {
    month,
    dueDate: card.nextDueDate ?? null,
    forecast: card.forecasts?.find((item) => item.month === month) ?? null,
  };
}
