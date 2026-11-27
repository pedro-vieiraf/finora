import { useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { ConfirmDialog } from '@/components/feedback/confirm-dialog';
import { mobileColors, mobileFont, mobileRadius, mobileTouchTarget } from '@/constants/mobile-theme';
import { useFeedback } from '@/contexts/feedback-context';
import { usePrivacy } from '@/contexts/privacy-context';
import { useTransactions } from '@/contexts/transactions-context';
import type { Account } from '@/services/accounts-api';
import { reconcileBankTransfer, type Transaction } from '@/services/transactions-api';
import { maskMoney } from '@/utils/privacy';

const dateFormatter = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
});

function bankDay(timestamp: string) {
  const parts = dateFormatter.formatToParts(new Date(timestamp));
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value);
  return Date.UTC(value('year'), value('month') - 1, value('day'));
}

export function ReconcileTransfer({ debit, accounts, onDone }: {
  debit: Transaction;
  accounts: Account[];
  onDone: () => void;
}) {
  const { transactions, refreshTransactions } = useTransactions();
  const { showToast } = useFeedback();
  const { hidden } = usePrivacy();
  const [selected, setSelected] = useState<Transaction | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const busy = useRef(false);

  const candidates = transactions.filter((credit) =>
    credit.source === 'OPEN_FINANCE' && credit.type === 'INCOME' && !credit.pending
    && credit.accountId !== debit.accountId && credit.currency === debit.currency
    && credit.amount === debit.amount
    && Math.abs(bankDay(credit.timestamp) - bankDay(debit.timestamp)) <= 86_400_000,
  );
  if (candidates.length === 0) return null;

  const accountName = (id: string) => accounts.find((account) => account.id === id)?.name ?? 'Outra conta';
  const amount = maskMoney(new Intl.NumberFormat('pt-BR', { style: 'currency', currency: debit.currency })
    .format(debit.amount), hidden);

  async function reconcile(selectedCredit: Transaction) {
    if (busy.current) return;
    busy.current = true;
    setSaving(true);
    setError(null);
    try {
      await reconcileBankTransfer(debit.id, selectedCredit.id);
      await refreshTransactions({ silent: true }).catch(() => undefined);
      showToast({ variant: 'success', title: 'Transferência interna registrada' });
      onDone();
    } catch {
      setError('Não foi possível conciliar agora. Atualize o extrato e tente novamente.');
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }

  return (
    <View style={styles.panel}>
      <Text style={styles.title}>Transferência entre minhas contas</Text>
      <Text style={styles.meta}>Selecione a entrada correspondente.</Text>
      {candidates.map((credit) => (
        <Pressable
          key={credit.id}
          accessibilityRole="button"
          onPress={() => { setError(null); setSelected(credit); }}
          style={({ pressed }) => [styles.option, pressed && styles.pressed]}
        >
          <View style={styles.details}>
            <Text style={styles.title}>{accountName(credit.accountId)}</Text>
            <Text style={styles.meta}>
              {credit.customDescription || credit.description || 'Entrada'} · {dateFormatter.format(new Date(credit.timestamp))}
            </Text>
          </View>
          <Text style={styles.amount}>{amount}</Text>
        </Pressable>
      ))}
      {selected ? (
        <ConfirmDialog
          visible
          tone="primary"
          title="Conciliar transferência?"
          message={`${amount} de ${accountName(debit.accountId)} para ${accountName(selected.accountId)}.`}
          confirmLabel="Conciliar"
          loading={saving}
          error={error}
          onConfirm={() => void reconcile(selected)}
          onCancel={() => setSelected(null)}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    padding: 12, gap: 8, borderWidth: 1, borderColor: mobileColors.border,
    borderRadius: mobileRadius.medium, backgroundColor: mobileColors.surface,
  },
  title: { color: mobileColors.text, fontFamily: mobileFont, fontWeight: '700' },
  meta: { color: mobileColors.textSecondary, fontFamily: mobileFont, fontSize: 12 },
  option: {
    flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: mobileTouchTarget,
    padding: 10, borderRadius: mobileRadius.small, backgroundColor: mobileColors.surfaceMuted,
  },
  details: { flex: 1, minWidth: 0, gap: 4 },
  amount: { color: mobileColors.text, fontFamily: mobileFont, fontVariant: ['tabular-nums'] },
  pressed: { opacity: 0.75 },
});
