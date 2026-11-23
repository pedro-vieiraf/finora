import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { desktopColors, desktopFont } from '@/components/desktop/desktop-theme';
import { mobileColors, mobileFont } from '@/constants/mobile-theme';
import { usePrivacy } from '@/contexts/privacy-context';
import {
  getCreditCardInvoice,
  type CreditCardInvoiceDetails,
} from '@/services/credit-card-invoices-api';
import type { OpenFinanceCard } from '@/services/open-finance-card-bills-api';
import type { OutstandingInvoiceEntry } from '@/utils/credit-cards';
import { maskMoney } from '@/utils/privacy';

const monthFormatter = new Intl.DateTimeFormat('pt-BR', {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

function money(amount: number, currency = 'BRL') {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(amount);
}

type Props = {
  month: string;
  total: number;
  entries: readonly OutstandingInvoiceEntry[];
  connectedCards: readonly OpenFinanceCard[];
  desktop: boolean;
};

export function InvoiceEvolutionDetails({ month, total, entries, connectedCards, desktop }: Props) {
  const { hidden } = usePrivacy();
  const colors = desktop ? desktopColors : mobileColors;
  const text = { color: colors.text, fontFamily: desktop ? desktopFont : mobileFont };
  const secondary = { ...text, color: colors.textSecondary };
  const invoices = entries.filter(({ invoice }) => invoice.dueDate.startsWith(month));
  const forecasts = connectedCards.flatMap((card) =>
    (card.forecasts ?? [])
      .filter((forecast) => forecast.month === month && forecast.currency === 'BRL')
      .map((forecast) => ({ card, forecast })),
  );

  return (
    <View style={[styles.panel, { borderColor: colors.border, backgroundColor: colors.surface }]}>
      <View style={styles.row}>
        <Text accessibilityRole="header" style={[styles.title, text]}>
          {monthFormatter.format(new Date(`${month}-01T00:00:00Z`))}
        </Text>
        <Text style={[styles.title, text]}>{maskMoney(money(total), hidden)}</Text>
      </View>
      {invoices.length === 0 && forecasts.length === 0 ? (
        <Text style={secondary}>Nenhuma fatura ou previsão para este mês.</Text>
      ) : null}

      {invoices.map(({ card, invoice }) => (
        <View key={invoice.id} style={[styles.group, { borderColor: colors.border }]}>
          <View style={styles.row}>
            <Text style={[styles.label, text]}>{card.name} · Em aberto</Text>
            <Text style={text}>{maskMoney(money(invoice.remainingAmount, invoice.currency), hidden)}</Text>
          </View>
          {invoice.paidAmount > 0 ? (
            <Text style={secondary}>
              Pagamentos desta fatura: {maskMoney(money(invoice.paidAmount, invoice.currency), hidden)}
            </Text>
          ) : null}
          <ManualInvoicePurchases
            cardId={card.id}
            invoiceId={invoice.id}
            text={text}
            hidden={hidden}
          />
        </View>
      ))}

      {forecasts.map(({ card, forecast }) => (
        <View key={card.id} style={[styles.group, { borderColor: colors.border }]}>
          <View style={styles.row}>
            <Text style={[styles.label, text]}>
              {card.bankName} · {forecast.estimated ? 'Estimativa' : 'Previsão'}
            </Text>
            <Text style={text}>{maskMoney(money(forecast.total), hidden)}</Text>
          </View>
          {forecast.entries?.length ? forecast.entries.map((entry) => (
            <View key={entry.id} style={styles.row}>
              <Text style={[styles.label, secondary]}>
                {entry.description || (entry.direction === 'CREDIT' ? 'Crédito' : 'Compra')}
                {entry.totalInstallments && entry.totalInstallments > 1 && entry.installmentNumber
                  ? ` · ${entry.installmentNumber}/${entry.totalInstallments}` : ''}
              </Text>
              {entry.amount !== null ? (
                <Text style={text}>{maskMoney(money(entry.amount), hidden)}</Text>
              ) : null}
            </View>
          )) : (
            <Text style={secondary}>Detalhes dos lançamentos indisponíveis.</Text>
          )}
        </View>
      ))}
    </View>
  );
}

function ManualInvoicePurchases({ cardId, invoiceId, text, hidden }: {
  cardId: string;
  invoiceId: string;
  text: { color: string; fontFamily: string };
  hidden: boolean;
}) {
  const [result, setResult] = useState<CreditCardInvoiceDetails | 'error' | null>(null);

  useEffect(() => {
    let active = true;
    void getCreditCardInvoice(cardId, invoiceId).then(
      (details) => { if (active) setResult(details); },
      () => { if (active) setResult('error'); },
    );
    return () => { active = false; };
  }, [cardId, invoiceId]);

  if (result === null) return <Text style={text}>Carregando compras…</Text>;
  if (result === 'error') return <Text style={text}>Não foi possível carregar as compras.</Text>;

  const rows = result.purchases.flatMap((purchase) =>
    purchase.installments.filter((part) => part.invoiceId === invoiceId)
      .map((part) => (
        <View key={part.id} style={styles.row}>
          <Text style={[styles.label, text]}>
            {purchase.description}
            {purchase.installmentCount > 1 ? ` · ${part.number}/${purchase.installmentCount}` : ''}
          </Text>
          <Text style={text}>{maskMoney(money(part.amount, purchase.currency), hidden)}</Text>
        </View>
      )),
  );

  return rows.length ? rows : <Text style={text}>Nenhuma compra disponível.</Text>;
}

const styles = StyleSheet.create({
  panel: { marginTop: 12, padding: 14, borderWidth: 1, borderRadius: 12, gap: 12 },
  title: { fontSize: 14, fontWeight: '700' },
  group: { borderTopWidth: 1, paddingTop: 10, gap: 8 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: 10 },
  label: { flexShrink: 1 },
});
