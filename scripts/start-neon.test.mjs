import assert from 'node:assert/strict';
import { test } from 'node:test';

import { neonDatasource } from './start-neon.mjs';

test('converts the Neon URL to JDBC with verified TLS and separate credentials', () => {
  assert.deepEqual(neonDatasource(
    'postgresql://example%2Brole:example%40pass@sample-pooler.example.test:5432/finora?sslmode=require&channel_binding=require',
  ), {
    SPRING_DATASOURCE_URL: 'jdbc:postgresql://sample-pooler.example.test:5432/finora?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require',
    SPRING_DATASOURCE_USERNAME: 'example+role',
    SPRING_DATASOURCE_PASSWORD: 'example@pass',
  });
});

test('rejects missing and invalid URLs with a fixed error message', () => {
  for (const input of [undefined, 'invalid-input', 'postgres://role@localhost/finora']) {
    assert.throws(() => neonDatasource(input), /^Error: DATABASE_URL must be a PostgreSQL/);
  }
});
