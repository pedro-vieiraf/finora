import { useMemo } from 'react';

import { useCreditCards } from '@/contexts/credit-cards-context';
import type { OpenFinanceCard } from '@/services/open-finance-card-bills-api';

import {
  addConnectedForecasts,
  buildInvoiceEvolution,
  formatEvolutionPeriod,
  getInvoiceEvolutionReference,
  getOutstandingInvoiceEntries,
  getVisibleInvoiceEntries,
  summarizeInvoiceEntries,
  trimEvolutionToData,
} from '@/utils/credit-cards';

const NO_CONNECTED_CARDS: readonly OpenFinanceCard[] = [];

/**
 * Faturas pendentes exibidas no Dashboard.
 *
 * Reutiliza o `CreditCardsProvider`, que já mantém cartões e faturas
 * carregados, e apenas seleciona e soma os valores informados pelo backend.
 * Nenhum valor financeiro é recalculado aqui.
 */
export function useDashboardCreditCards(connectedCards = NO_CONNECTED_CARDS) {
  const {
    cards,
    invoicesByCard,
    loading,
    error,
    invoicesError,
    refreshCreditCards,
  } = useCreditCards();

  const entries = useMemo(
    () => getOutstandingInvoiceEntries(cards, invoicesByCard),
    [cards, invoicesByCard],
  );

  const summary = useMemo(() => summarizeInvoiceEntries(entries), [entries]);

  const list = useMemo(() => getVisibleInvoiceEntries(entries), [entries]);

  const evolution = useMemo(() => {
    const start = getInvoiceEvolutionReference(entries, connectedCards);
    const points = addConnectedForecasts(buildInvoiceEvolution(entries, start), connectedCards);

    // Sem uma consulta de faturas confiável não é possível afirmar que os
    // meses zerados realmente não têm faturas, então o gráfico mostra apenas
    // o período coberto pelos valores conhecidos.
    return invoicesError ? trimEvolutionToData(points) : points;
  }, [entries, invoicesError, connectedCards]);

  const evolutionPeriod = useMemo(
    () => formatEvolutionPeriod(evolution),
    [evolution],
  );

  return {
    entries,
    visibleEntries: list.visible,
    hiddenInvoiceCount: list.hiddenCount,
    pendingTotal: summary.total,
    pendingCount: summary.count,
    nextDueDate: summary.nextDueDate,
    nextDueOverdue: summary.nextDueOverdue,
    evolution,
    evolutionPeriod,
    hasEvolutionData: evolution.some((point) => point.total > 0),
    hasForecast: connectedCards.some((card) => card.forecasts?.some((item) => item.currency === 'BRL')),
    loading,
    error,
    invoicesError,
    refresh: refreshCreditCards,
  };
}
