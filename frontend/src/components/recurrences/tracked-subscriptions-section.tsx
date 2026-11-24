import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect } from 'expo-router';

import { InlineFeedback } from '@/components/feedback/inline-feedback';
import { formatCurrency, formatRecurrenceDate } from '@/components/recurrences/recurrence-utils';
import { mobileColors, mobileFont, mobileRadius } from '@/constants/mobile-theme';
import { useFeedback } from '@/contexts/feedback-context';
import { usePrivacy } from '@/contexts/privacy-context';
import {
  getTrackedSubscriptions, removeTrackedSubscription, type TrackedSubscription,
} from '@/services/tracked-subscriptions-api';
import { maskMoney } from '@/utils/privacy';

export function TrackedSubscriptionsSection() {
  const { hidden } = usePrivacy();
  const { showToast } = useFeedback();
  const [subscriptions, setSubscriptions] = useState<TrackedSubscription[]>([]);
  const [unavailable, setUnavailable] = useState(false);

  useFocusEffect(useCallback(() => {
    let active = true;
    void getTrackedSubscriptions().then((loaded) => {
      if (!active) return;
      setSubscriptions(loaded);
      setUnavailable(false);
    }).catch(() => {
      if (active) setUnavailable(true);
    });
    return () => { active = false; };
  }, []));

  async function stopTracking(id: string) {
    try {
      await removeTrackedSubscription(id);
      setSubscriptions((current) => current.filter((subscription) => subscription.id !== id));
    } catch {
      showToast({ variant: 'error', title: 'Não foi possível parar de acompanhar' });
    }
  }

  if (unavailable && subscriptions.length === 0) {
    return <InlineFeedback variant="warning" message="Assinaturas do Open Finance indisponíveis." />;
  }
  if (subscriptions.length === 0) return null;

  return (
    <View style={styles.section}>
      <Text accessibilityRole="header" style={styles.title}>Acompanhadas pelo Open Finance</Text>
      {unavailable ? (
        <InlineFeedback variant="warning" message="Não foi possível atualizar as cobranças." />
      ) : null}
      {subscriptions.map((item) => (
        <View key={item.id} style={styles.card}>
          <Text style={styles.title}>{item.description}</Text>
          <Text style={styles.meta}>
            {item.accountLabel} · {item.source === 'CARD' ? 'Cartão' : 'Conta'}
          </Text>
          <Text style={styles.meta}>
            Última cobrança em {formatRecurrenceDate(item.lastChargeDate)} · {maskMoney(formatCurrency(item.amount, item.currency), hidden)}
          </Text>
          <Text style={styles.meta}>Próxima prevista em {formatRecurrenceDate(item.nextChargeDate)}</Text>
          {!item.sourceAvailable ? <Text style={styles.meta}>Fonte temporariamente indisponível.</Text> : null}
          <Pressable accessibilityRole="button" onPress={() => void stopTracking(item.id)} style={styles.button}>
            <Text style={styles.action}>Parar de acompanhar</Text>
          </Pressable>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { gap: 10, marginBottom: 18 },
  card: { padding: 14, gap: 6, borderRadius: mobileRadius.medium, backgroundColor: mobileColors.surface },
  title: { color: mobileColors.text, fontFamily: mobileFont, fontWeight: '700' },
  meta: { color: mobileColors.textSecondary, fontFamily: mobileFont },
  button: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  action: { color: mobileColors.primary, fontFamily: mobileFont, fontWeight: '700' },
});
