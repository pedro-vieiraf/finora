import { useMemo } from 'react';

import { useAccounts } from '@/contexts/accounts-context';
import { useTransactions } from '@/contexts/transactions-context';
import { filterOperationalTransactions } from '@/utils/operational-transactions';

export function useOperationalTransactions() {
  const { accounts } = useAccounts();
  const context = useTransactions();
  const { transactions } = context;
  const operational = useMemo(
    () => filterOperationalTransactions(transactions, accounts),
    [transactions, accounts],
  );

  return { ...context, transactions: operational, statement: transactions };
}
