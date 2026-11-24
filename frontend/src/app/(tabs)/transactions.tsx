
import { useCallback, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect } from 'expo-router';
import { SymbolView } from 'expo-symbols';

import { useOperationalTransactions } from '@/hooks/use-operational-transactions';
import {
  getAccounts,
  type Account,
} from '../../services/accounts-api';
import {
  getCategories,
  type Category,
} from '../../services/categories-api';
import {
  getPendingBankDebits,
  type PendingBankDebit,
  type Transaction as ApiTransaction,
  type TransactionType as ApiTransactionType,
} from '../../services/transactions-api';
import { DesktopTransactions } from '@/components/desktop/transactions/desktop-transactions';
import { MobileScreenHeader } from '@/components/mobile/mobile-screen-header';
import {
  TransactionFormModal,
  type TransactionFormMode,
} from '@/components/transactions/transaction-form-modal';
import { useFeedback } from '@/contexts/feedback-context';
import { usePrivacy } from '@/contexts/privacy-context';
import { useIsDesktopLayout } from '@/hooks/use-is-desktop-layout';
import { mobileIcons } from '@/constants/mobile-icons';
import { mobileColors, mobileFont, mobileLayout, mobileTouchTarget } from '@/constants/mobile-theme';
import { maskMoney } from '@/utils/privacy';
import { sortTransactionsByTimestampDescending } from '../../utils/transaction-sort';

type TransactionType = 'income' | 'expense' | 'transfer';
type FilterType = 'all' | TransactionType;

type Transaction = {
  id: string;
  description: string;
  category: string;
  account: string;
  date: string;
  timestamp: string;
  amount: number;
  currency: string;
  type: TransactionType;
  isRecurring: boolean;
};

const COLORS = {
  background: mobileColors.background,
  surface: mobileColors.surface,
  primary: mobileColors.primary,
  primaryLight: mobileColors.primarySoft,
  text: mobileColors.text,
  secondary: mobileColors.textSecondary,
  border: mobileColors.border,
  income: mobileColors.income,
  incomeBackground: mobileColors.incomeSoft,
  expense: mobileColors.expense,
  expenseBackground: mobileColors.expenseSoft,
  transfer: mobileColors.transfer,
  transferBackground: mobileColors.transferSoft,
};

const FILTERS: { label: string; value: FilterType }[] = [
  { label: 'Todas', value: 'all' },
  { label: 'Receitas', value: 'income' },
  { label: 'Despesas', value: 'expense' },
  { label: 'Transferências', value: 'transfer' },
];

function formatCurrency(value: number, currency: string) {
  try {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency,
    }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
}

function formatMonth(date: Date) {
  const formatted = new Intl.DateTimeFormat('pt-BR', {
    month: 'long',
    year: 'numeric',
  }).format(date);

  return formatted.charAt(0).toUpperCase() + formatted.slice(1);
}

function formatDateHeading(dateString: string) {
  const [year, month, day] = dateString.split('-').map(Number);
  const date = new Date(year, month - 1, day, 12);

  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'long',
  }).format(date);
}

function getLocalDateKey(timestamp: string) {
  const date = new Date(timestamp);

  if (Number.isNaN(date.getTime())) {
    return timestamp.slice(0, 10);
  }

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function mapTransactionType(type: ApiTransactionType): TransactionType {
  switch (type) {
    case 'INCOME':
      return 'income';
    case 'EXPENSE':
      return 'expense';
    case 'TRANSFER':
      return 'transfer';
  }
}

function getTransactionColor(type: TransactionType) {
  if (type === 'income') return COLORS.income;
  if (type === 'transfer') return COLORS.transfer;
  return COLORS.expense;
}

function getTransactionBackground(type: TransactionType) {
  if (type === 'income') return COLORS.incomeBackground;
  if (type === 'transfer') return COLORS.transferBackground;
  return COLORS.expenseBackground;
}

function getAmountPrefix(type: TransactionType) {
  if (type === 'income') return '+ ';
  if (type === 'expense') return '− ';
  return '';
}

function getDefaultDescription(type: TransactionType) {
  if (type === 'income') return 'Receita';
  if (type === 'transfer') return 'Transferência';
  return 'Despesa';
}

function mapApiTransaction(
    transaction: ApiTransaction,
    accountsById: Map<string, Account>,
    categoriesById: Map<string, Category>,
): Transaction {
  const type = mapTransactionType(transaction.type);
  const sourceAccount = accountsById.get(transaction.accountId);
  const destinationAccount = transaction.destinationAccountId
      ? accountsById.get(transaction.destinationAccountId)
      : undefined;

  const category = transaction.categoryId
      ? categoriesById.get(transaction.categoryId)
      : undefined;

  let accountLabel = sourceAccount?.name ?? 'Conta excluída';

  if (type === 'transfer' && transaction.destinationAccountId) {
    accountLabel =
        `${accountLabel} → ${destinationAccount?.name ?? 'Conta excluída'}`;
  }

  return {
    id: transaction.id,
    description:
        transaction.customDescription?.trim() ||
        transaction.description?.trim() || getDefaultDescription(type),
    category:
        type === 'transfer'
            ? 'Transferência'
            : category?.name ?? 'Sem categoria',
    account: accountLabel,
    date: getLocalDateKey(transaction.timestamp),
    timestamp: transaction.timestamp,
    amount: transaction.amount,
    currency: transaction.currency,
    type,
    isRecurring: Boolean(transaction.recurrenceId),
  };
}

function TransactionRow({
                          transaction,
                          onPress,
                        }: {
  transaction: Transaction;
  onPress: () => void;
}) {
  const color = getTransactionColor(transaction.type);
  const backgroundColor = getTransactionBackground(transaction.type);
  const { hidden } = usePrivacy();

  return (
      <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Ver detalhes de ${transaction.description}`}
          onPress={onPress}
          style={({ pressed }) => [
            styles.transactionRow,
            pressed && styles.pressedRow,
          ]}
      >
        <View style={[styles.transactionIcon, { backgroundColor }]}>
          <SymbolView name={mobileIcons[transaction.type]} size={19} tintColor={color} />
        </View>

        <View style={styles.transactionDetails}>
          <View style={styles.transactionTitleRow}>
            <Text style={styles.transactionDescription} numberOfLines={1}>
              {transaction.description}
            </Text>
            {transaction.isRecurring ? (
              <View style={styles.recurringBadge}>
                <Text style={styles.recurringBadgeText}>Recorrente</Text>
              </View>
            ) : null}
          </View>
          <Text style={styles.transactionMeta} numberOfLines={1}>
            {transaction.category} · {transaction.account}
          </Text>
        </View>

        <Text
            style={[styles.transactionAmount, { color }]}
            numberOfLines={1}
        >
          {maskMoney(
            `${getAmountPrefix(transaction.type)}${formatCurrency(
              transaction.amount,
              transaction.currency,
            )}`,
            hidden,
          )}
        </Text>
      </Pressable>
  );
}

function TransactionsMobileScreen() {
  const { showToast } = useFeedback();
  const { hidden } = usePrivacy();
  const {
    transactions: apiTransactions,
    loading: transactionsLoading,
    error: transactionsError,
    refreshTransactions,
  } = useOperationalTransactions();

  const [monthOffset, setMonthOffset] = useState(0);
  const [selectedFilter, setSelectedFilter] =
      useState<FilterType>('all');
  const [searchQuery, setSearchQuery] = useState('');

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [pendingDebits, setPendingDebits] = useState<PendingBankDebit[]>([]);
  const [referencesLoading, setReferencesLoading] = useState(true);
  const [screenError, setScreenError] = useState<string | null>(null);

  const [modalVisible, setModalVisible] = useState(false);
  const [formMode, setFormMode] =
      useState<TransactionFormMode>('create');
  const [editingTransactionId, setEditingTransactionId] =
      useState<string | null>(null);

  const loadScreenData = useCallback(async () => {
    setReferencesLoading(true);
    setScreenError(null);

    try {
      const [loadedAccounts, loadedCategories] = await Promise.all([
        getAccounts(),
        getCategories(),
        refreshTransactions(),
      ]).then(([accountData, categoryData]) => [
        accountData,
        categoryData,
      ] as const);

      setAccounts(loadedAccounts);
      setCategories(loadedCategories);
    } catch (cause) {
      setScreenError(
          cause instanceof Error
              ? cause.message
              : 'Não foi possível carregar os dados das transações.',
      );
    } finally {
      setReferencesLoading(false);
    }
  }, [refreshTransactions]);

  useFocusEffect(
      useCallback(() => {
        void loadScreenData();
        let active = true;
        function refreshPendingDebits() {
          void getPendingBankDebits().then((debits) => {
            if (active) setPendingDebits(debits);
          }).catch(() => {
            // A indisponibilidade dos pendentes não bloqueia o histórico.
          });
        }
        refreshPendingDebits();

        // O scheduler do backend pode gerar ocorrências recorrentes enquanto
        // esta tela permanece aberta. Atualiza silenciosamente para que elas
        // apareçam no histórico sem exigir troca de aba ou recarga manual.
        const intervalId = setInterval(() => {
          refreshPendingDebits();
          void refreshTransactions({ silent: true }).catch(() => {
            // O erro já fica disponível no estado do context.
          });
        }, 10_000);

        return () => {
          active = false;
          clearInterval(intervalId);
        };
      }, [loadScreenData, refreshTransactions]),
  );

  const activeAccounts = useMemo(
      () => accounts.filter((account) => account.status === 'ACTIVE'),
      [accounts],
  );

  const accountsById = useMemo(
      () => new Map(accounts.map((account) => [account.id, account])),
      [accounts],
  );

  const categoriesById = useMemo(
      () =>
          new Map(categories.map((category) => [category.id, category])),
      [categories],
  );

  const mappedTransactions = useMemo(
      () =>
          sortTransactionsByTimestampDescending(
              apiTransactions.map((transaction) =>
                  mapApiTransaction(
                      transaction,
                      accountsById,
                      categoriesById,
                  ),
              ),
          ),
      [apiTransactions, accountsById, categoriesById],
  );

  const selectedMonth = useMemo(() => {
    const now = new Date();

    return new Date(
        now.getFullYear(),
        now.getMonth() + monthOffset,
        1,
    );
  }, [monthOffset]);

  const monthKey = [
    selectedMonth.getFullYear(),
    String(selectedMonth.getMonth() + 1).padStart(2, '0'),
  ].join('-');

  const visiblePendingDebits = pendingDebits.filter((debit) => {
    if (selectedFilter !== 'all' && selectedFilter !== 'expense') return false;
    if (!getLocalDateKey(debit.timestamp).startsWith(monthKey)) return false;
    const account = accountsById.get(debit.accountId);
    const text = `${debit.description ?? 'Débito no cartão'} ${account?.name ?? ''}`;
    return text.toLocaleLowerCase('pt-BR').includes(searchQuery.trim().toLocaleLowerCase('pt-BR'));
  });

  const monthTransactions = useMemo(
      () =>
          mappedTransactions.filter((transaction) =>
              transaction.date.startsWith(monthKey),
          ),
      [mappedTransactions, monthKey],
  );

  const summary = useMemo(
      () =>
          monthTransactions.reduce(
              (totals, transaction) => {
                if (transaction.currency !== 'BRL') {
                  return totals;
                }

                if (transaction.type === 'income') {
                  totals.income += transaction.amount;
                } else if (transaction.type === 'expense') {
                  totals.expense += transaction.amount;
                } else {
                  totals.transfer += transaction.amount;
                }

                return totals;
              },
              { income: 0, expense: 0, transfer: 0 },
          ),
      [monthTransactions],
  );

  const filteredTransactions = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLocaleLowerCase('pt-BR');

    return monthTransactions.filter((transaction) => {
      const matchesFilter =
          selectedFilter === 'all' ||
          transaction.type === selectedFilter;

      const searchableText = [
        transaction.description,
        transaction.category,
        transaction.account,
      ]
      .join(' ')
      .toLocaleLowerCase('pt-BR');

      return (
          matchesFilter &&
          (!normalizedQuery || searchableText.includes(normalizedQuery))
      );
    });
  }, [monthTransactions, searchQuery, selectedFilter]);

  const groupedTransactions = useMemo(() => {
    const groups: { date: string; transactions: Transaction[] }[] = [];

    for (const transaction of filteredTransactions) {
      const lastGroup = groups[groups.length - 1];

      if (lastGroup?.date === transaction.date) {
        lastGroup.transactions.push(transaction);
      } else {
        groups.push({
          date: transaction.date,
          transactions: [transaction],
        });
      }
    }

    return groups;
  }, [filteredTransactions]);

  const openCreateModal = () => {
    if (activeAccounts.length === 0) {
      showToast({
        variant: 'warning',
        title: 'Nenhuma conta ativa',
        message: 'Crie ou reative uma conta antes de registrar uma transação.',
      });
      return;
    }

    setFormMode('create');
    setEditingTransactionId(null);
    setModalVisible(true);
  };

  const openEditModal = (transactionId: string) => {
    const transaction = apiTransactions.find(
        (item) => item.id === transactionId,
    );

    if (!transaction) {
      showToast({
        variant: 'error',
        title: 'Transação indisponível',
        message:
            'Não foi possível encontrar essa transação nos dados atuais. Atualize a tela e tente novamente.',
      });
      return;
    }

    setFormMode('edit');
    setEditingTransactionId(transaction.id);
    setModalVisible(true);
  };

  const closeForm = () => {
    setModalVisible(false);
    setEditingTransactionId(null);
  };

  const loading = referencesLoading || transactionsLoading;
  const error = screenError ?? transactionsError;

  return (
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        <ScrollView
            style={styles.scrollView}
            contentContainerStyle={styles.content}
            showsVerticalScrollIndicator={false}
            keyboardShouldPersistTaps="handled"
        >
          <MobileScreenHeader
            eyebrow="finora"
            title="Transações"
            primaryAction={{
              accessibilityLabel: 'Adicionar transação',
              onPress: openCreateModal,
            }}
          />

          <View style={styles.monthSelector}>
            <Pressable
                accessibilityRole="button"
                accessibilityLabel="Mês anterior"
                onPress={() => setMonthOffset((current) => current - 1)}
                style={styles.monthArrow}
            >
              <SymbolView name={mobileIcons.previous} size={22} tintColor={COLORS.primary} />
            </Pressable>

            <Text style={styles.monthTitle}>
              {formatMonth(selectedMonth)}
            </Text>

            <Pressable
                accessibilityRole="button"
                accessibilityLabel="Próximo mês"
                onPress={() =>
                    setMonthOffset((current) => Math.min(current + 1, 0))
                }
                disabled={monthOffset === 0}
                style={[
                  styles.monthArrow,
                  monthOffset === 0 && styles.disabledArrow,
                ]}
            >
              <SymbolView name={mobileIcons.next} size={22} tintColor={COLORS.primary} />
            </Pressable>
          </View>

          <View style={styles.summaryCard}>
            <Text style={styles.summaryLabel}>Resultado do período</Text>
            <Text
                style={[
                  styles.summaryBalance,
                  summary.income - summary.expense < 0 &&
                  styles.negativeBalance,
                ]}
            >
              {maskMoney(formatCurrency(summary.income - summary.expense, 'BRL'), hidden)}
            </Text>
            <Text style={styles.summaryHint}>
              Receitas menos despesas, sem transferências
            </Text>

            <View style={styles.summaryDivider} />

            <View style={styles.summaryMetrics}>
              <View style={styles.summaryMetric}>
                <View style={styles.metricHeading}>
                  <View
                      style={[
                        styles.metricDot,
                        { backgroundColor: COLORS.income },
                      ]}
                  />
                  <Text style={styles.metricLabel}>Receitas</Text>
                </View>
                <Text style={[styles.metricValue, { color: COLORS.income }]}>
                  {maskMoney(formatCurrency(summary.income, 'BRL'), hidden)}
                </Text>
              </View>

              <View style={styles.metricSeparator} />

              <View style={styles.summaryMetric}>
                <View style={styles.metricHeading}>
                  <View
                      style={[
                        styles.metricDot,
                        { backgroundColor: COLORS.expense },
                      ]}
                  />
                  <Text style={styles.metricLabel}>Despesas</Text>
                </View>
                <Text style={[styles.metricValue, { color: COLORS.expense }]}>
                  {maskMoney(formatCurrency(summary.expense, 'BRL'), hidden)}
                </Text>
              </View>
            </View>
          </View>

          <View style={styles.transferSummary}>
            <View style={styles.transferIcon}>
              <SymbolView name={mobileIcons.transfer} size={19} tintColor={COLORS.transfer} />
            </View>
            <View style={styles.transferDetails}>
              <Text style={styles.transferTitle}>Transferências</Text>
              <Text style={styles.transferHint}>
                Movimentações entre contas
              </Text>
            </View>
            <Text style={styles.transferAmount}>
              {maskMoney(formatCurrency(summary.transfer, 'BRL'), hidden)}
            </Text>
          </View>

          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Histórico</Text>
            <Text style={styles.transactionCount}>
              {filteredTransactions.length}{' '}
              {filteredTransactions.length === 1
                  ? 'movimentação'
                  : 'movimentações'}
            </Text>
          </View>

          <View id="search-field-transactions-mobile" style={styles.searchContainer}>
            <SymbolView name={mobileIcons.search} size={20} tintColor={COLORS.secondary} />
            <TextInput
                value={searchQuery}
                onChangeText={setSearchQuery}
                placeholder="Buscar transação, categoria ou conta"
                placeholderTextColor={COLORS.secondary}
                style={styles.searchInput}
                returnKeyType="search"
                accessibilityLabel="Buscar transações"
            />
            {searchQuery.length > 0 && (
                <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="Limpar busca"
                    onPress={() => setSearchQuery('')}
                    style={styles.searchClearButton}
                >
                  <SymbolView name={mobileIcons.close} size={20} tintColor={COLORS.secondary} />
                </Pressable>
            )}
          </View>

          <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.filters}
          >
            {FILTERS.map((filter) => {
              const selected = selectedFilter === filter.value;

              return (
                  <Pressable
                      key={filter.value}
                      accessibilityRole="button"
                      accessibilityState={{ selected }}
                      onPress={() => setSelectedFilter(filter.value)}
                      style={[
                        styles.filterChip,
                        selected && styles.filterChipSelected,
                      ]}
                  >
                    <Text
                        style={[
                          styles.filterText,
                          selected && styles.filterTextSelected,
                        ]}
                    >
                      {filter.label}
                    </Text>
                  </Pressable>
              );
            })}
          </ScrollView>

          {visiblePendingDebits.length > 0 ? (
            <View style={styles.pendingSection}>
              <Text style={styles.sectionTitle}>Débitos pendentes</Text>
              <Text style={styles.pendingMeta}>Entram no resumo após a confirmação do banco.</Text>
              {visiblePendingDebits.map((debit) => (
                <View key={`${debit.accountId}:${debit.id}`} style={styles.pendingRow}>
                  <View style={styles.pendingDetails}>
                    <Text style={styles.pendingDescription}>
                      {debit.description || 'Débito no cartão'}
                    </Text>
                    <Text style={styles.pendingMeta}>
                      {accountsById.get(debit.accountId)?.name ?? 'Open Finance'} · {formatDateHeading(getLocalDateKey(debit.timestamp))}
                    </Text>
                  </View>
                  <Text style={styles.pendingDescription}>
                    {maskMoney(formatCurrency(debit.amount, debit.currency), hidden)}
                  </Text>
                </View>
              ))}
            </View>
          ) : null}

          {loading ? (
              <View style={styles.statusState}>
                <ActivityIndicator size="large" color={COLORS.primary} />
                <Text style={styles.emptyDescription}>
                  Carregando transações...
                </Text>
              </View>
          ) : error ? (
              <View style={styles.emptyState}>
                <Text style={styles.emptyTitle}>
                  Não foi possível carregar as transações
                </Text>
                <Text style={styles.emptyDescription}>{error}</Text>
                <Pressable
                    accessibilityRole="button"
                    onPress={() => void loadScreenData()}
                    style={styles.retryButton}
                >
                  <Text style={styles.retryButtonText}>Tentar novamente</Text>
                </Pressable>
              </View>
          ) : groupedTransactions.length === 0 && visiblePendingDebits.length > 0 ? null
            : groupedTransactions.length === 0 ? (
              <View style={styles.emptyState}>
                <SymbolView name={mobileIcons.search} size={26} tintColor={COLORS.primary} />
                <Text style={styles.emptyTitle}>
                  Nenhuma transação encontrada
                </Text>
                <Text style={styles.emptyDescription}>
                  Tente outro filtro, outro termo de busca ou selecione um mês
                  diferente. Você também pode registrar uma nova transação.
                </Text>
                <Pressable
                    accessibilityRole="button"
                    onPress={openCreateModal}
                    style={styles.retryButton}
                >
                  <Text style={styles.retryButtonText}>Adicionar transação</Text>
                </Pressable>
              </View>
          ) : (
              groupedTransactions.map((group) => (
                  <View key={group.date} style={styles.dateGroup}>
                    <Text style={styles.dateHeading}>
                      {formatDateHeading(group.date)}
                    </Text>

                    <View style={styles.transactionList}>
                      {group.transactions.map((transaction) => (
                          <TransactionRow
                              key={transaction.id}
                              transaction={transaction}
                              onPress={() => openEditModal(transaction.id)}
                          />
                      ))}
                    </View>
                  </View>
              ))
          )}

        </ScrollView>

        {modalVisible ? (
          <TransactionFormModal
              mode={formMode}
              transactionId={editingTransactionId}
              accounts={accounts}
              categories={categories}
              onClose={closeForm}
          />
        ) : null}
      </SafeAreaView>
  );
}

export default function TransactionsScreen() {
  const isDesktop = useIsDesktopLayout();

  if (isDesktop) {
    return <DesktopTransactions />;
  }

  return <TransactionsMobileScreen />;
}

const styles = StyleSheet.create({
  pendingSection: { gap: 8, marginBottom: 16 },
  pendingRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    gap: 12, backgroundColor: mobileColors.surfaceMuted, padding: 12, borderRadius: 10,
  },
  pendingDetails: { flex: 1, gap: 4 },
  pendingDescription: { color: mobileColors.text, fontFamily: mobileFont, fontWeight: '700' },
  pendingMeta: { color: mobileColors.textSecondary, fontFamily: mobileFont, fontSize: 12 },

  safeArea: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  scrollView: {
    flex: 1,
  },
  content: {
    paddingHorizontal: mobileLayout.horizontalPadding,
    paddingTop: mobileLayout.contentPaddingTop,
    paddingBottom: mobileLayout.contentPaddingBottom,
    maxWidth: mobileLayout.contentMaxWidth,
    width: '100%',
    alignSelf: 'center',
  },
  monthSelector: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 18,
  },
  monthArrow: {
    width: 38,
    height: 38,
    borderRadius: 13,
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  disabledArrow: {
    opacity: 0.35,
  },
  monthTitle: {
    color: COLORS.text,
    fontSize: 15,
    fontWeight: '700',
  },
  summaryCard: {
    backgroundColor: COLORS.surface,
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: COLORS.border,
    marginBottom: 12,
  },
  summaryLabel: {
    color: COLORS.secondary,
    fontSize: 12,
    fontWeight: '600',
  },
  summaryBalance: {
    fontFamily: mobileFont,
    color: COLORS.primary,
    fontSize: 30,
    fontWeight: '800',
    marginTop: 8,
    fontVariant: ['tabular-nums'],
  },
  negativeBalance: {
    color: COLORS.expense,
  },
  summaryHint: {
    color: COLORS.secondary,
    fontSize: 11,
    marginTop: 5,
  },
  summaryDivider: {
    height: 1,
    backgroundColor: COLORS.border,
    marginVertical: 18,
  },
  summaryMetrics: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  summaryMetric: {
    flex: 1,
  },
  metricHeading: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  metricDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
  },
  metricLabel: {
    color: COLORS.secondary,
    fontSize: 11,
  },
  metricValue: {
    fontFamily: mobileFont,
    fontSize: 14,
    fontWeight: '700',
    marginTop: 8,
    fontVariant: ['tabular-nums'],
  },
  metricSeparator: {
    width: 1,
    height: 38,
    backgroundColor: COLORS.border,
    marginHorizontal: 18,
  },
  transferSummary: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.transferBackground,
    borderRadius: 16,
    padding: 14,
    marginBottom: 26,
  },
  transferIcon: {
    width: 38,
    height: 38,
    borderRadius: 13,
    backgroundColor: COLORS.surface,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 11,
  },
  transferDetails: {
    flex: 1,
  },
  transferTitle: {
    color: COLORS.text,
    fontSize: 12,
    fontWeight: '700',
  },
  transferHint: {
    color: COLORS.secondary,
    fontSize: 10,
    marginTop: 4,
  },
  transferAmount: {
    fontFamily: mobileFont,
    color: COLORS.transfer,
    fontSize: 13,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  sectionTitle: {
    color: COLORS.text,
    fontSize: 18,
    fontWeight: '800',
  },
  transactionCount: {
    color: COLORS.secondary,
    fontSize: 11,
    fontWeight: '600',
  },
  searchContainer: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 14,
    paddingHorizontal: 13,
    marginBottom: 13,
  },
  searchClearButton: {
    width: mobileTouchTarget,
    height: mobileTouchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchInput: {
    fontFamily: mobileFont,
    flex: 1,
    color: COLORS.text,
    fontSize: 12,
    paddingVertical: 13,
  },
  filters: {
    gap: 8,
    paddingBottom: 20,
  },
  filterChip: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surface,
  },
  filterChipSelected: {
    backgroundColor: COLORS.primary,
    borderColor: COLORS.primary,
  },
  filterText: {
    color: COLORS.secondary,
    fontSize: 12,
    fontWeight: '600',
  },
  filterTextSelected: {
    color: '#FFFFFF',
  },
  dateGroup: {
    marginBottom: 22,
  },
  dateHeading: {
    color: COLORS.secondary,
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'capitalize',
    marginBottom: 10,
  },
  transactionList: {
    backgroundColor: COLORS.surface,
    borderRadius: 17,
    borderWidth: 1,
    borderColor: COLORS.border,
    paddingHorizontal: 13,
  },
  transactionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    gap: 10,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
  },
  pressedRow: {
    opacity: 0.7,
  },
  transactionIcon: {
    width: 42,
    height: 42,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  transactionDetails: {
    flex: 1,
    minWidth: 0,
  },
  transactionTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  recurringBadge: {
    flexShrink: 0,
    borderRadius: 999,
    backgroundColor: COLORS.primaryLight,
    paddingHorizontal: 7,
    paddingVertical: 3,
  },
  recurringBadgeText: {
    color: COLORS.primary,
    fontSize: 9,
    fontWeight: '700',
  },
  transactionDescription: {
    flexShrink: 1,
    color: COLORS.text,
    fontSize: 13,
    fontWeight: '600',
  },
  transactionMeta: {
    color: COLORS.secondary,
    fontSize: 10,
    marginTop: 5,
  },
  transactionAmount: {
    fontFamily: mobileFont,
    flexShrink: 0,
    fontSize: 12,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  statusState: {
    alignItems: 'center',
    backgroundColor: COLORS.surface,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: COLORS.border,
    paddingHorizontal: 24,
    paddingVertical: 36,
  },
  emptyState: {
    alignItems: 'center',
    backgroundColor: COLORS.surface,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: COLORS.border,
    paddingHorizontal: 24,
    paddingVertical: 36,
  },
  emptyTitle: {
    color: COLORS.text,
    fontSize: 15,
    fontWeight: '700',
    textAlign: 'center',
  },
  emptyDescription: {
    color: COLORS.secondary,
    fontSize: 12,
    lineHeight: 19,
    textAlign: 'center',
    marginTop: 8,
  },
  retryButton: {
    marginTop: 16,
    backgroundColor: COLORS.primaryLight,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 12,
  },
  retryButtonText: {
    color: COLORS.primary,
    fontSize: 12,
    fontWeight: '700',
    textAlign: 'center',
  },
});
