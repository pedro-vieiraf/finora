import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { SecureStoreOptions } from 'expo-secure-store';

import { createRememberedApiAccess } from './remembered-api-access.ts';

test('protects the saved key with biometrics and scopes it to the API address', async () => {
  const vault = new Map<string, string>();
  function address(key: string, options?: SecureStoreOptions) {
    assert.equal(options?.requireAuthentication, true);
    return `${options?.keychainService}:${key}`;
  }
  const storage = {
    getItemAsync: async (key: string, options?: SecureStoreOptions) =>
      vault.get(address(key, options)) ?? null,
    setItemAsync: async (key: string, value: string, options?: SecureStoreOptions) => {
      vault.set(address(key, options), value);
    },
    deleteItemAsync: async (key: string, options?: SecureStoreOptions) => {
      vault.delete(address(key, options));
    },
  };
  const first = createRememberedApiAccess('https://first.example', storage);
  const second = createRememberedApiAccess('https://second.example', storage);
  const sample = 'a'.repeat(64);

  assert.equal(await first.read(), null);
  await first.save(sample);
  assert.equal(await first.read(), sample);
  assert.equal(await second.read(), null);
  await first.remove();
  assert.equal(await first.read(), null);
});
