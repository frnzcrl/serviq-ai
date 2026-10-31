/** Checkpoint 17C: one same-origin fetch gateway for the existing workspaces. */
export type StaffSession = { email: string; roles: string[] };
type CsrfToken = { headerName: string; token: string };

const nativeFetch = window.fetch.bind(window);
let csrf: CsrfToken | null = null;
let pendingCsrf: Promise<CsrfToken> | null = null;
let installed = false;

function isServiqApi(input: RequestInfo | URL): boolean {
  const url = new URL(input instanceof Request ? input.url : String(input), window.location.href);
  return url.origin === window.location.origin && url.pathname.startsWith('/api/v1/');
}

export async function refreshCsrf(): Promise<CsrfToken> {
  if (!pendingCsrf) {
    pendingCsrf = nativeFetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error(`Cannot initialize CSRF protection (${response.status})`);
        const token = await response.json() as CsrfToken;
        if (!token.headerName || !token.token) throw new Error('Invalid CSRF response');
        csrf = token;
        return token;
      }).finally(() => { pendingCsrf = null; });
  }
  return pendingCsrf;
}

export function installServiqAuthFetch(): void {
  if (installed) return;
  installed = true;
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    if (!isServiqApi(input)) return nativeFetch(input, init);

    // Request headers and method can come from either an input Request or init.
    const original = input instanceof Request ? input : null;
    const method = (init?.method ?? original?.method ?? 'GET').toUpperCase();
    const headers = new Headers(original?.headers);
    if (init?.headers) new Headers(init.headers).forEach((value, key) => headers.set(key, value));
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const token = csrf ?? await refreshCsrf();
      headers.set(token.headerName, token.token);
    }
    const response = await nativeFetch(input, { ...init, credentials: 'same-origin', headers });
    const url = new URL(original?.url ?? String(input), location.href);
    if (response.status === 401 && !url.pathname.startsWith('/api/v1/auth/')) {
      window.dispatchEvent(new Event('serviq:session-expired'));
    }
    return response;
  };
}

export async function currentStaff(): Promise<StaffSession | null> {
  const response = await fetch('/api/v1/auth/me', { cache: 'no-store' });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error(`Session check failed (${response.status})`);
  return response.json() as Promise<StaffSession>;
}

export async function signIn(email: string, password: string): Promise<StaffSession> {
  await refreshCsrf();
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: email.trim(), password }),
  });
  if (!response.ok) {
    if (response.status === 401) throw new Error('Incorrect email or password.');
    throw new Error(`Sign-in failed (${response.status})`);
  }
  const staff = await response.json() as StaffSession;
  // Spring Security may rotate the session and CSRF state on successful sign-in.
  csrf = null;
  await refreshCsrf();
  return staff;
}

export async function signOut(): Promise<void> {
  const response = await fetch('/api/v1/auth/logout', { method: 'POST' });
  if (!response.ok) throw new Error(`Logout failed (${response.status})`);
  csrf = null;
  // Deliberately do not fetch /auth/csrf until next sign-in.
}
