import { useRef, useState, type PropsWithChildren } from 'react';
import * as SecureStore from 'expo-secure-store';
import { Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { mobileColors, mobileFont } from '@/constants/mobile-theme';
import { useFeedback } from '@/contexts/feedback-context';
import { API_URL, ApiError, requiresApiToken, signInToApi } from '@/services/api';

import { createRememberedApiAccess } from '@/services/remembered-api-access';

const rememberedAccess = createRememberedApiAccess(API_URL ?? '', SecureStore);

export function ApiAccessGate({ children }: PropsWithChildren) {
  const { showToast } = useFeedback();
  const [biometricsAvailable] = useState(
    () => requiresApiToken && Platform.OS === 'android' && SecureStore.canUseBiometricAuthentication(),
  );
  const [allowed, setAllowed] = useState(!requiresApiToken);
  const [token, setToken] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const signingIn = useRef(false);

  async function signIn(useBiometrics = false) {
    if (signingIn.current) return;

    const enteredToken = token.trim();
    if (!useBiometrics && !/^[0-9a-f]{64}$/i.test(enteredToken)) {
      setError('Confira a chave de acesso.');
      return;
    }

    signingIn.current = true;
    setPending(true);
    setError('');
    try {
      let accessKey = enteredToken;
      if (useBiometrics) {
        try {
          accessKey = await rememberedAccess.read() ?? '';
        } catch {
          setError('Não foi possível desbloquear. Tente a biometria de novo ou use a chave.');
          return;
        }
        if (!accessKey) {
          setError('Cole sua chave para ativar o acesso com biometria.');
          return;
        }
      }

      await signInToApi(accessKey);
      if (!useBiometrics && biometricsAvailable) {
        try {
          await rememberedAccess.save(accessKey);
        } catch {
          showToast({
            variant: 'warning',
            title: 'Acesso não salvo',
            message: 'Na próxima abertura, entre novamente com sua chave.',
          });
        }
      }
      setToken('');
      setAllowed(true);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        if (useBiometrics) {
          await rememberedAccess.remove().catch(() => undefined);
          setError('A chave salva perdeu a validade. Cole a chave atual para entrar.');
        } else {
          setError('Chave de acesso incorreta.');
        }
      } else {
        setError('Não foi possível conectar ao Finora. Tente novamente em instantes.');
      }
    } finally {
      signingIn.current = false;
      setPending(false);
    }
  }

  if (allowed) return children;

  return (
    <View style={styles.screen}>
      <View style={styles.panel}>
        <Text accessibilityRole="header" style={styles.title}>Entrar no Finora</Text>
        <TextInput
          accessibilityLabel="Chave de acesso"
          autoCapitalize="none"
          autoCorrect={false}
          editable={!pending}
          maxLength={64}
          onChangeText={setToken}
          onSubmitEditing={() => void signIn()}
          placeholder="Chave de acesso"
          placeholderTextColor={mobileColors.textSecondary}
          secureTextEntry
          style={styles.input}
          value={token}
        />
        {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ disabled: pending, busy: pending }}
          disabled={pending}
          onPress={() => void signIn()}
          style={styles.button}
        >
          <Text style={styles.buttonText}>{pending ? 'Conectando…' : 'Entrar'}</Text>
        </Pressable>
        {biometricsAvailable ? (
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled: pending }}
            disabled={pending}
            onPress={() => void signIn(true)}
            style={styles.biometricButton}
          >
            <Text style={styles.biometricLabel}>Entrar com biometria</Text>
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24,
    backgroundColor: mobileColors.background,
  },
  panel: {
    width: '100%', maxWidth: 420, padding: 24, gap: 16, borderRadius: 18,
    backgroundColor: mobileColors.surface,
  },
  title: { fontFamily: mobileFont, fontSize: 22, fontWeight: '700', color: mobileColors.text },
  input: {
    minHeight: 48, paddingHorizontal: 12, borderWidth: 1, borderRadius: 10,
    borderColor: mobileColors.borderStrong, color: mobileColors.text, fontFamily: mobileFont,
  },
  error: { fontFamily: mobileFont, color: mobileColors.expense },
  button: {
    alignItems: 'center', justifyContent: 'center', minHeight: 48, borderRadius: 10,
    backgroundColor: mobileColors.primary,
  },
  biometricButton: { alignItems: 'center', justifyContent: 'center', minHeight: 44 },
  biometricLabel: { fontFamily: mobileFont, fontWeight: '700', color: mobileColors.primary },
  buttonText: { fontFamily: mobileFont, fontWeight: '700', color: mobileColors.surface },
});
