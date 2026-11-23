import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

export function neonDatasource(databaseUrl) {
  try {
    const url = new URL(databaseUrl);
    if (!['postgres:', 'postgresql:'].includes(url.protocol) || !url.hostname ||
        !url.username || !url.password || url.pathname.length < 2) {
      throw new Error();
    }
    const options = new URLSearchParams({
      sslmode: 'verify-full',
      sslfactory: 'org.postgresql.ssl.DefaultJavaSSLFactory',
    });
    if (url.searchParams.get('channel_binding') === 'require') {
      options.set('channelBinding', 'require');
    }
    return {
      SPRING_DATASOURCE_URL: `jdbc:postgresql://${url.host}${url.pathname}?${options}`,
      SPRING_DATASOURCE_USERNAME: decodeURIComponent(url.username),
      SPRING_DATASOURCE_PASSWORD: decodeURIComponent(url.password),
    };
  } catch {
    throw new Error('DATABASE_URL must be a PostgreSQL URL with a database, role, and password.');
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const { DATABASE_URL, DATABASE_URL_UNPOOLED, ...env } = process.env;
    const datasource = neonDatasource(DATABASE_URL);
    const jar = fileURLToPath(new URL('../backend/target/finora-0.0.1-SNAPSHOT.jar', import.meta.url));
    const result = spawnSync('java', [
      '-jar', jar,
      '--spring.profiles.active=neon',
      '--spring.sql.init.mode=never',
      '--spring.jpa.hibernate.ddl-auto=none',
    ], { env: { ...env, ...datasource }, stdio: 'inherit' });
    if (result.error) throw new Error('Could not start Java. Check that Java 21 is on PATH.');
    process.exitCode = result.status ?? 1;
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
