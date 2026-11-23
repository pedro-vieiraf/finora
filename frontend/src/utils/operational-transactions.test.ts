import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { Account } from '../services/accounts-api.ts';
import type { Transaction } from '../services/transactions-api.ts';
import { filterOperationalTransactions } from './operational-transactions.ts';

const accounts: Account[] = [
  { id: 'banrisul', name: 'Banrisul', type: 'CHECKING', status: 'ACTIVE', source: 'OPEN_FINANCE', balance: 100 },
  { id: 'nubank', name: 'Nubank', type: 'CHECKING', status: 'ACTIVE', source: 'OPEN_FINANCE', balance: 50 },
];

function transaction(id: string, overrides: Partial<Transaction>): Transaction {
  return {
    id, accountId: 'banrisul', destinationAccountId: null, categoryId: null,
    amount: 25, currency: 'BRL', description: 'APLICAÇÃO AUTOMÁTICA CDB',
    timestamp: '2026-11-22T12:00:00Z', source: 'OPEN_FINANCE', type: 'EXPENSE',
    recurrenceId: null, occurrenceDate: null, ...overrides,
  };
}

test('oculta somente aplicações e resgates automáticos importados do Banrisul', () => {
  const transactions = [
    transaction('aplicacao', { customDescription: 'Investimento' }),
    transaction('resgate', { type: 'INCOME', description: '  Resgate   CDB Automático ' }),
    transaction('manual', { source: 'MANUAL' }),
    transaction('outro-banco', { accountId: 'nubank' }),
    transaction('investimento-manual', { description: 'Aplicação CDB' }),
    transaction('compra', { description: 'Pix mercado', customDescription: 'APLICAÇÃO AUTOMÁTICA CDB' }),
    transaction('transferencia', { type: 'TRANSFER' }),
  ];
  assert.deepEqual(filterOperationalTransactions(transactions, accounts).map(({ id }) => id),
    ['manual', 'outro-banco', 'investimento-manual', 'compra', 'transferencia']);
  assert.equal(transactions.length, 7);
  assert.equal(accounts[0].balance, 100);
});
