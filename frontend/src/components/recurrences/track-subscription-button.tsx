import { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';

import { mobileColors, mobileFont } from '@/constants/mobile-theme';
import { useFeedback } from '@/contexts/feedback-context';
import { ApiError } from '@/services/api';
import { trackSubscription, type SubscriptionCharge } from '@/services/tracked-subscriptions-api';

export function TrackSubscriptionButton({ charge }: { charge: SubscriptionCharge }) {
  const { showToast } = useFeedback();
  const [saving, setSaving] = useState(false);

  async function save() {
    if (saving) return;
    setSaving(true);
    try {
      await trackSubscription(charge);
      showToast({ variant: 'success', title: 'Assinatura acompanhada' });
    } catch (error) {
      showToast({
        variant: 'error',
        title: 'Não foi possível acompanhar a cobrança',
        message: error instanceof ApiError && error.status === 404
          ? 'Use uma cobrança efetivada do Open Finance.'
          : 'Tente novamente.',
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ busy: saving, disabled: saving }}
      disabled={saving}
      onPress={() => void save()}
      style={styles.button}
    >
      <Text style={styles.label}>
        {saving ? 'Salvando…' : 'Acompanhar como assinatura'}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  label: { color: mobileColors.primary, fontFamily: mobileFont, fontWeight: '700' },
});
