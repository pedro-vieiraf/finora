import { useState } from 'react';
import { SymbolView } from 'expo-symbols';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { desktopFont } from '@/components/desktop/desktop-theme';
import { mobileColors, mobileFont, mobileRadius } from '@/constants/mobile-theme';
import { TrackSubscriptionButton } from '@/components/recurrences/track-subscription-button';
import { usePrivacy } from '@/contexts/privacy-context';
import type {
  OpenFinanceBill,
  OpenFinanceCard,
} from '@/services/open-finance-card-bills-api';
import { formatDateKey, formatMonthYear } from '@/utils/credit-cards';
import { maskMoney } from '@/utils/privacy';
import { describeNextConnectedBill } from '@/utils/open-finance-card-bills';

function formatAmount(amount: number | null, currency: string | null, hidden: boolean) {
  if (amount == null || !currency) return 'Valor indisponível';

  return maskMoney(
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(amount),
    hidden,
  );
}

function BillRow({
  bill,
  cardId,
  allowTracking,
  fontFamily,
  hidden,
}: {
  bill: OpenFinanceBill;
  cardId: string;
  allowTracking: boolean;
  fontFamily: string;
  hidden: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  const [selectedCharge, setSelectedCharge] = useState<string | null>(null);
  const textFont = { fontFamily };
  const date = formatDateKey(bill.dueDate);

  return (
    <View style={styles.bill}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Fatura com vencimento em ${date}`}
        accessibilityState={{ expanded }}
        onPress={() => setExpanded((current) => !current)}
        style={({ pressed }) => [styles.billHeader, pressed && styles.pressed]}
      >
        <View style={styles.billHeading}>
          <Text style={[styles.billDate, textFont]}>Vencimento em {date}</Text>
          <Text style={[styles.meta, textFont]}>
            {expanded ? 'Ocultar detalhes' : 'Ver detalhes'}
          </Text>
        </View>
        <Text style={[styles.total, textFont]}>
          {formatAmount(bill.total, bill.currency, hidden)}
        </Text>
      </Pressable>

      {expanded ? (
        <View style={styles.details}>
          {bill.closingDate ? (
            <Text style={[styles.meta, textFont]}>
              Fechamento em {formatDateKey(bill.closingDate)}
            </Text>
          ) : null}

          <Text style={[styles.detailTitle, textFont]}>Pagamentos informados</Text>
          {bill.payments.length === 0 ? (
            <Text style={[styles.meta, textFont]}>Nenhum pagamento informado pelo banco.</Text>
          ) : bill.payments.map((payment) => (
            <View key={payment.id} style={styles.detailRow}>
              <Text style={[styles.detailLabel, textFont]}>
                {payment.date ? formatDateKey(payment.date) : 'Data indisponível'}
              </Text>
              <Text style={[styles.money, textFont]}>
                {formatAmount(payment.amount, payment.currency ?? bill.currency, hidden)}
              </Text>
            </View>
          ))}

          <Text style={[styles.detailTitle, textFont]}>Lançamentos vinculados</Text>
          {bill.entries.length === 0 ? (
            <Text style={[styles.meta, textFont]}>
              O banco não retornou lançamentos vinculados a esta fatura no período.
            </Text>
          ) : bill.entries.map((entry) => {
            const description = entry.description || 'Lançamento do cartão';
            const canRegister = allowTracking && entry.subscriptionEligible === true;
            const expandedCharge = selectedCharge === entry.id;
            const row = (
              <>
                <View style={styles.detailLabel}>
                  <Text style={[styles.entryName, textFont]}>{description}</Text>
                  <Text style={[styles.meta, textFont]}>
                    {[
                      entry.date ? formatDateKey(entry.date.slice(0, 10)) : null,
                      entry.direction === 'CREDIT' ? 'Crédito' : entry.direction === 'DEBIT' ? 'Débito' : null,
                      entry.installmentNumber && entry.totalInstallments
                        ? `Parcela ${entry.installmentNumber}/${entry.totalInstallments}` : null,
                    ].filter(Boolean).join(' · ')}
                  </Text>
                </View>
                <Text style={[styles.money, textFont]}>
                  {formatAmount(entry.amount, entry.currency ?? bill.currency, hidden)}
                </Text>
              </>
            );

            return (
              <View key={entry.id} style={styles.chargeCard}>
                {canRegister ? (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`Detalhes de ${description}, ${formatAmount(entry.amount, entry.currency ?? bill.currency, hidden)}`}
                    accessibilityState={{ expanded: expandedCharge }}
                    onPress={() => setSelectedCharge(expandedCharge ? null : entry.id)}
                    style={({ pressed }) => [styles.chargeRow, pressed && styles.pressed]}
                  >
                    {row}
                    <SymbolView
                      name={expandedCharge
                        ? { ios: 'chevron.up', android: 'expand_less', web: 'expand_less' }
                        : { ios: 'chevron.down', android: 'expand_more', web: 'expand_more' }}
                      size={16}
                      tintColor={mobileColors.textSecondary}
                    />
                  </Pressable>
                ) : <View style={styles.chargeRow}>{row}</View>}
                {canRegister && expandedCharge ? (
                  <View style={styles.chargeAction}>
                    <TrackSubscriptionButton
                      charge={{ source: 'CARD', cardId, chargeId: entry.id }}
                      description={description}
                    />
                  </View>
                ) : null}
              </View>
            );
          })}
        </View>
      ) : null}
    </View>
  );
}

export function OpenFinanceCardItem({
  card,
  desktop = false,
  dashboard = false,
}: {
  card: OpenFinanceCard;
  desktop?: boolean;
  dashboard?: boolean;
}) {
  const { hidden } = usePrivacy();
  const fontFamily = desktop ? desktopFont : mobileFont;
  const textFont = { fontFamily };

  return (
    <View style={[styles.card, desktop && !dashboard && styles.desktopCard]}>
      <View style={styles.cardHeading}>
        <Text style={[styles.bankName, textFont]}>{card.bankName}</Text>
        <Text style={[styles.sourceBadge, textFont]}>Open Finance</Text>
      </View>
      <OpenBalance card={card} fontFamily={fontFamily} hidden={hidden} dashboard={dashboard} />
      {dashboard ? null : card.bills.length === 0 ? (
        <Text style={[styles.meta, textFont]}>Nenhuma fatura disponível no período.</Text>
      ) : card.bills.map((bill) => (
        <BillRow key={bill.id} bill={bill} cardId={card.id} allowTracking={!desktop} fontFamily={fontFamily} hidden={hidden} />
      ))}
    </View>
  );
}

function OpenBalance({ card, fontFamily, hidden, dashboard }: {
  card: OpenFinanceCard;
  fontFamily: string;
  hidden: boolean;
  dashboard: boolean;
}) {
  const { dueDate, forecast, month } = describeNextConnectedBill(card);
  const textFont = { fontFamily };
  return (
    <View style={styles.details}>
      <Text style={[styles.detailTitle, textFont]}>
        {dueDate
          ? `Próxima fatura · ${formatDateKey(dueDate)}`
          : month
            ? `Próximo ciclo · ${formatMonthYear(`${month}-01`)}`
            : 'Próximo vencimento não informado'}
      </Text>
      {forecast ? (
        <View style={styles.detailRow}>
          <Text style={[styles.detailLabel, textFont]}>
            {forecast.estimated ? 'Estimativa do ciclo' : 'Previsão do ciclo'}
          </Text>
          <Text style={[styles.total, textFont]}>{formatAmount(forecast.total, forecast.currency, hidden)}</Text>
        </View>
      ) : (
        <Text style={[styles.meta, textFont]}>O banco ainda não informou valores para este ciclo.</Text>
      )}
      {!dashboard && card.openBalance != null ? (
        <View style={styles.detailRow}>
          <Text style={[styles.detailLabel, textFont]}>Saldo geral em aberto</Text>
          <Text style={[styles.total, textFont]}>{formatAmount(card.openBalance, card.currency, hidden)}</Text>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  amountColumn: { alignItems: 'flex-end', gap: 3 },
  cardHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  desktopCard: { flexGrow: 1, flexBasis: 320, maxWidth: 460 },
  sourceBadge: {
    backgroundColor: mobileColors.primarySoft, borderRadius: mobileRadius.pill,
    color: mobileColors.primary, fontSize: 10, fontWeight: '700',
    paddingHorizontal: 8, paddingVertical: 4,
  },
  card: {
    backgroundColor: mobileColors.surface,
    borderColor: mobileColors.border,
    borderRadius: mobileRadius.large,
    borderWidth: 1,
    padding: 14,
    gap: 8,
  },
  bankName: { color: mobileColors.primary, fontSize: 14, fontWeight: '700' },
  bill: { borderTopColor: mobileColors.border, borderTopWidth: 1 },
  billHeader: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    paddingVertical: 10,
  },
  billHeading: { flex: 1, gap: 3 },
  pressed: { opacity: 0.7 },
  billDate: { color: mobileColors.text, fontSize: 14, fontWeight: '600' },
  meta: { color: mobileColors.textSecondary, fontSize: 12 },
  total: { color: mobileColors.primary, fontSize: 15, fontWeight: '700', fontVariant: ['tabular-nums'] },
  details: { gap: 8, paddingBottom: 12 },
  detailTitle: { color: mobileColors.text, fontSize: 13, fontWeight: '700', marginTop: 5 },
  chargeCard: {
    borderBottomWidth: 1,
    borderBottomColor: mobileColors.border,
  },
  chargeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 48,
    gap: 10,
    paddingVertical: 9,
  },
  chargeAction: {
    marginBottom: 10,
    padding: 10,
    borderRadius: mobileRadius.medium,
    backgroundColor: mobileColors.surfaceMuted,
  },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 },
  detailLabel: { flex: 1, color: mobileColors.text, fontSize: 13 },
  entryName: { color: mobileColors.text, fontSize: 13, fontWeight: '700' },
  money: { color: mobileColors.text, fontSize: 13, fontVariant: ['tabular-nums'] },
});
