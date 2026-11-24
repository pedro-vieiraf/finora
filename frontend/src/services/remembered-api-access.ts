import type * as SecureStore from 'expo-secure-store';

type AccessStorage = Pick<typeof SecureStore, 'getItemAsync' | 'setItemAsync' | 'deleteItemAsync'>;

export function createRememberedApiAccess(apiUrl: string, storage: AccessStorage) {
  const key = 'finora-api-token';
  const options: SecureStore.SecureStoreOptions = {
    keychainService: `finora-api-access:${apiUrl}`,
    requireAuthentication: true,
    authenticationPrompt: 'Confirme sua biometria para acessar o Finora',
  };

  return {
    read: () => storage.getItemAsync(key, options),
    save: (token: string) => storage.setItemAsync(key, token, options),
    remove: () => storage.deleteItemAsync(key, options),
  };
}
