import type { Account } from '../services/accounts-api';
import type { Transaction } from '../services/transactions-api';

function normalize(value: string) {
  return value.normalize('NFD').replace(/\p{M}/gu, '').trim().replace(/\s+/g, ' ').toUpperCase();
}

export function filterOperationalTransactions(
  transactions: readonly Transaction[],
  accounts: readonly Account[],
): Transaction[] {
  const banrisulIds = new Set(
    accounts
      .filter((account) => account.source === 'OPEN_FINANCE' && normalize(account.name) === 'BANRISUL')
      .map((account) => account.id),
  );

  return transactions.filter((transaction) => {
    if (transaction.source !== 'OPEN_FINANCE' || !banrisulIds.has(transaction.accountId)
        || !transaction.description) return true;

    const description = normalize(transaction.description);
    return !(
      (transaction.type === 'INCOME' && description === 'RESGATE CDB AUTOMATICO')
      || (transaction.type === 'EXPENSE' && description === 'APLICACAO AUTOMATICA CDB')
    );
  });
}
