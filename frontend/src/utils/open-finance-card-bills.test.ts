import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { OpenFinanceCard } from '../services/open-finance-card-bills-api.ts';
import { addConnectedForecasts, buildInvoiceEvolution, getInvoiceEvolutionReference } from './credit-cards.ts';
import { describeNextConnectedBill } from './open-finance-card-bills.ts';

const card: OpenFinanceCard = {
  id: 'nubank', bankName: 'Nubank', openBalance: 135, currency: 'BRL', nextDueDate: null,
  nextCycle: '2026-12',
  bills: [{ id: 'novembro', dueDate: '2026-11-26', closingDate: null, total: 100,
    currency: 'BRL', payments: [], entries: [] }],
  forecasts: [{ month: '2027-01', total: 15, currency: 'BRL' }],
};

test('não usa o saldo geral nem a fatura anterior para um mês sem projeção', () => {
  assert.deepEqual(describeNextConnectedBill(card), { month: '2026-12', dueDate: null, forecast: null });
});

test('seleciona somente a previsão do próximo ciclo, deixando os meses seguintes separados', () => {
  const december = { month: '2026-12', total: 55.43, currency: 'BRL' };
  assert.deepEqual(describeNextConnectedBill({
    ...card, nextDueDate: '2026-12-26', forecasts: [...(card.forecasts ?? []), december],
  }), { month: '2026-12', dueDate: '2026-12-26', forecast: december });
});

test('mostra os seis ciclos de dezembro até maio quando novembro não tem fatura manual pendente', () => {
  const today = new Date(2026, 10, 22, 12);
  const connected = { ...card, forecasts: [{ month: '2026-12', total: 55, currency: 'BRL' }] };
  const reference = getInvoiceEvolutionReference([], [connected], today);
  assert.deepEqual(buildInvoiceEvolution([], reference).map(({ key }) => key),
    ['2026-12', '2027-01', '2027-02', '2027-03', '2027-04', '2027-05']);
  assert.equal(getInvoiceEvolutionReference([], [card], today), today);
});

test('agrega previsões BRL ao gráfico no mês correspondente sem mexer nas faturas manuais', () => {
  const points = [{ key: '2026-12', total: 10 }, { key: '2027-01', total: 5 }];
  const otherCard = { ...card, forecasts: [
    { month: '2026-12', total: 2.34, currency: 'BRL' },
    { month: '2026-12', total: 7, currency: 'USD' },
  ] };
  assert.deepEqual(addConnectedForecasts(points, [card, otherCard]), [
    { key: '2026-12', total: 12.34, estimated: 2.34 },
    { key: '2027-01', total: 20, estimated: 15 },
  ]);
  assert.equal(points[0].total, 10);
});
