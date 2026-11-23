import { apiRequest } from './api';

type OpenFinancePayment = {
  id: string;
  date: string | null;
  amount: number | null;
  currency: string | null;
};

type OpenFinanceBillEntry = {
  id: string;
  date: string | null;
  description: string | null;
  direction: string | null;
  amount: number | null;
  currency: string | null;
  installmentNumber: number | null;
  totalInstallments: number | null;
};

export type OpenFinanceBill = {
  id: string;
  dueDate: string;
  closingDate: string | null;
  total: number | null;
  currency: string | null;
  payments: OpenFinancePayment[];
  entries: OpenFinanceBillEntry[];
};

export type OpenFinanceCardForecast = {
  month: string;
  total: number;
  currency: string;
  estimated?: boolean;
  entries?: OpenFinanceBillEntry[];
};

export type OpenFinanceCard = {
  id: string;
  bankName: string;
  openBalance: number | null;
  currency: string | null;
  nextDueDate: string | null;
  nextCycle?: string;
  bills: OpenFinanceBill[];
  forecasts?: OpenFinanceCardForecast[];
};

export function getOpenFinanceCardBills(): Promise<OpenFinanceCard[]> {
  return apiRequest<OpenFinanceCard[]>('/credit-cards/open-finance/bills');
}
