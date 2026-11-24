import { Platform } from 'react-native';

/**
 * No Android o `localhost` aponta para o próprio aparelho, então a URL da API
 * pode ser configurada separadamente (por exemplo, com o IP do computador na
 * rede Tailscale). As demais plataformas continuam usando EXPO_PUBLIC_API_URL.
 */
export const API_URL = Platform.OS === 'android'
    ? process.env.EXPO_PUBLIC_API_URL_ANDROID ?? process.env.EXPO_PUBLIC_API_URL
    : process.env.EXPO_PUBLIC_API_URL;
const REQUEST_TIMEOUT_MS = 10_000;
const SIGN_IN_TIMEOUT_MS = 90_000;
export const requiresApiToken = process.env.EXPO_PUBLIC_API_AUTH_REQUIRED === 'true';
let apiToken: string | undefined;

export async function signInToApi(token: string): Promise<void> {
  if (!API_URL?.startsWith('https://')) {
    throw new Error('Não foi possível conectar ao Finora.');
  }
  await request('/auth/session', {}, token, SIGN_IN_TIMEOUT_MS);
  apiToken = token;
}

if (!API_URL) {
  throw new Error(
      'EXPO_PUBLIC_API_URL não está configurada. Verifique o arquivo .env. ' +
      'No Android, EXPO_PUBLIC_API_URL_ANDROID tem prioridade.',
  );
}

export class ApiError extends Error {
  constructor(
      message: string,
      public readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Erro para falhas em que não dá para saber se o servidor processou a
 * requisição (timeout ou queda de conexão). Repetir só é seguro quando a
 * operação é idempotente.
 */
export class AmbiguousRequestError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'AmbiguousRequestError';
  }
}

export async function apiRequest<T>(
    path: string,
    options: RequestInit = {},
): Promise<T> {
  return request<T>(path, options, apiToken, REQUEST_TIMEOUT_MS);
}

async function request<T>(
    path: string,
    options: RequestInit,
    token: string | undefined,
    timeoutMs: number,
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const controller = new AbortController();
  let timedOut = false;

  const timeoutId = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  let response: Response;

  try {
    response = await fetch(`${API_URL}${path}`, {
      ...options,
      signal: controller.signal,
      redirect: token ? 'error' : options.redirect,
      headers,
    });
  } catch {
    if (timedOut) {
      throw new AmbiguousRequestError(
          'A solicitação demorou demais e não foi possível confirmar ' +
          'se ela foi concluída.',
      );
    }

    throw new AmbiguousRequestError(
        'Não foi possível conectar ao Finora. Verifique sua conexão ' +
        'e tente novamente.',
    );
  } finally {
    clearTimeout(timeoutId);
  }

  if (!response.ok) {
    throw new ApiError(
        response.status === 401
          ? 'Seu acesso não foi autorizado. Entre novamente.'
          : 'Não foi possível concluir a solicitação. Tente novamente.',
        response.status,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
