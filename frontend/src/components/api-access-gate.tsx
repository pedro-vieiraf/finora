import { useRef, useState, type PropsWithChildren } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { mobileColors, mobileFont } from '@/constants/mobile-theme';
import { ApiError, requiresApiToken, signInToApi } from '@/services/api';

export function ApiAccessGate({ children }: PropsWithChildren) {
  const [allowed, setAllowed] = useState(!requiresApiToken);
  const [token, setToken] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const signingIn = useRef(false);

  async function signIn() {
    if (signingIn.current) return;

    const enteredToken = token.trim();
    if (!/^[0-9a-f]{64}$/i.test(enteredToken)) {
      setError('Confira a chave de acesso.');
      return;
    }

    signingIn.current = true;
    setPending(true);
    setError('');
    try {
      await signInToApi(enteredToken);
      setToken('');
      setAllowed(true);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 401
        ? 'Chave de acesso incorreta.'
        : 'Não foi possível acessar a API. Confira o endereço HTTPS e tente novamente.');
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
  buttonText: { fontFamily: mobileFont, fontWeight: '700', color: mobileColors.surface },
});
