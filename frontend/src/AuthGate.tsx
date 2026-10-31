import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { currentStaff, installServiqAuthFetch, signIn, signOut, type StaffSession } from './authClient';
import './AuthGate.css';

// Install before App and its workspaces mount, so their existing fetch calls
// automatically carry the authenticated session and CSRF header.
installServiqAuthFetch();

type Status = 'checking' | 'guest' | 'authenticated' | 'unavailable';

export function AuthGate({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('checking');
  const [staff, setStaff] = useState<StaffSession | null>(null);
  const [email, setEmail] = useState('admin@serviq.local');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let mounted = true;
    currentStaff().then(user => {
      if (!mounted) return;
      setStaff(user);
      setStatus(user ? 'authenticated' : 'guest');
    }).catch(err => {
      if (!mounted) return;
      setError(err instanceof Error ? err.message : 'Cannot reach the backend');
      setStatus('unavailable');
    });
    const onExpired = () => {
      setStaff(null);
      setPassword('');
      setStatus('guest');
      setError('Your session expired. Please sign in again.');
    };
    window.addEventListener('serviq:session-expired', onExpired);
    return () => { mounted = false; window.removeEventListener('serviq:session-expired', onExpired); };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const user = await signIn(email, password);
      setStaff(user); setPassword(''); setStatus('authenticated');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to sign in');
    } finally { setBusy(false); }
  }

  async function logout() {
    setBusy(true); setError('');
    try {
      await signOut();
      setStaff(null); setPassword(''); setStatus('guest');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to sign out');
    } finally { setBusy(false); }
  }

  if (status === 'checking') return <div className="serviq-auth-page"><div className="serviq-auth-status">Checking your Serviq session…</div></div>;
  if (status === 'unavailable') return <div className="serviq-auth-page"><div className="serviq-auth-panel"><div className="serviq-auth-logo">S<span>·</span> SERVIQ AI</div><h1>Backend unavailable</h1><p>{error}</p><button className="serviq-auth-submit" onClick={() => window.location.reload()}>Retry connection</button></div></div>;
  if (status === 'authenticated' && staff) return <>
    <div className="serviq-auth-session" aria-label="Signed-in staff session">
      <span className="serviq-auth-user" title={staff.roles.join(', ')}>{staff.email}</span>
      <button type="button" onClick={() => void logout()} disabled={busy}>{busy ? 'Signing out…' : 'Sign out'}</button>
      {error && <span role="alert" className="serviq-auth-session-error">{error}</span>}
    </div>
    {children}
  </>;

  return <main className="serviq-auth-page"><div className="serviq-auth-glow" aria-hidden="true" />
    <section className="serviq-auth-panel">
      <div className="serviq-auth-logo"><span className="serviq-auth-mark">S<span>·</span></span><span>SERVIQ <em>AI</em></span></div>
      <div className="serviq-auth-eyebrow">SECURE OPERATIONS PLATFORM</div>
      <h1>Welcome back<span>.</span></h1>
      <p>Sign in to manage service requests, technicians, appointments, and work orders.</p>
      <form onSubmit={submit} className="serviq-auth-form">
        <label htmlFor="serviq-email">Work email</label>
        <input id="serviq-email" type="email" value={email} onChange={event => setEmail(event.target.value)} autoComplete="username" required />
        <label htmlFor="serviq-password">Password</label>
        <input id="serviq-password" type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" minLength={1} required />
        {error && <div role="alert" className="serviq-auth-error">{error}</div>}
        <button className="serviq-auth-submit" type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in to workspace'}<span aria-hidden="true">↗</span></button>
      </form>
      <div className="serviq-auth-note">Protected by Spring Security · Session-based access</div>
    </section>
    <div className="serviq-auth-footer">SERVIQ AI / SERVICE OPERATIONS, INTELLIGENTLY CONNECTED</div>
  </main>;
}
