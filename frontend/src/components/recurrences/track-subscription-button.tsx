import { useState } from 'react';
import { SymbolView } from 'expo-symbols';
import { Pressable, StyleSheet, Text } from 'react-native';

import { mobileColors, mobileFont, mobileRadius, mobileTouchTarget } from '@/constants/mobile-theme';
import { useFeedback } from '@/contexts/feedback-context';
import { ApiError } from '@/services/api';
import { trackSubscription, type SubscriptionCharge } from '@/services/tracked-subscriptions-api';

export function TrackSubscriptionButton({ charge, description }: {
  charge: SubscriptionCharge;
  description: string;
}) {
  const { showToast } = useFeedback();
  const [status, setStatus] = useState<'idle' | 'saving' | 'tracked'>('idle');

  async function save() {
    if (status !== 'idle') return;
    setStatus('saving');
    try {
      await trackSubscription(charge);
      setStatus('tracked');
      showToast({ variant: 'success', title: 'Assinatura registrada', message: description });
    } catch (error) {
      showToast({
        variant: 'error',
        title: 'Não foi possível registrar a assinatura',
        message: error instanceof ApiError && error.status === 404
          ? 'Esta cobrança não está disponível. Atualize a lista e tente novamente.'
          : 'Tente novamente.',
      });
      setStatus('idle');
    }
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={status === 'tracked'
        ? `Assinatura registrada: ${description}`
        : `Registrar ${description} como assinatura`}
      accessibilityState={{ busy: status === 'saving', disabled: status !== 'idle' }}
      disabled={status !== 'idle'}
      onPress={() => void save()}
      style={({ pressed }) => [styles.button, pressed && styles.pressed]}
    >
      <SymbolView
        name={status === 'tracked'
          ? { ios: 'checkmark', android: 'check', web: 'check' }
          : { ios: 'repeat', android: 'repeat', web: 'repeat' }}
        size={16}
        tintColor={mobileColors.primary}
      />
      <Text style={styles.label}>
        {status === 'saving' ? 'Salvando…'
          : status === 'tracked' ? 'Registrada' : 'Registrar como assinatura'}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: mobileTouchTarget,
    paddingHorizontal: 12,
    borderRadius: mobileRadius.small,
    backgroundColor: mobileColors.primarySoft,
  },
  pressed: { opacity: 0.75 },
  label: { color: mobileColors.primary, fontFamily: mobileFont, fontSize: 12, fontWeight: '700', flexShrink: 1 },
});
