import { useEffect, useState } from 'react';
import { apiGet } from '../api/client';

export function ConnectionStatus({ onReady }: { onReady?: () => void }) {
  const [state, setState] = useState<'checking' | 'ready' | 'offline'>('checking');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setState('checking');
    apiGet<{ status: string }>('/actuator/health')
      .then(result => { if (active) setState(result.status === 'UP' ? 'ready' : 'offline'); })
      .catch(() => { if (active) setState('offline'); });
    return () => { active = false; };
  }, [attempt]);
  return <div className={`connection-status ${state}`} role="status" aria-live="polite">
    <span className="connection-dot" aria-hidden="true" />
    <div><strong>{state === 'checking' ? 'Checking your workspace…' : state === 'ready' ? 'Your workspace is ready' : 'Your workspace is offline'}</strong>
      {state === 'offline' && <p>Open <b>Start Folio</b> in your project folder and wait for “Folio is ready”. Keep Docker Desktop running, then check again. This is a connection issue, not a password error.</p>}
      {state === 'offline' && <button type="button" className="text-button" onClick={() => setAttempt(n => n + 1)}>Check again</button>}
      {state === 'ready' && onReady && <button type="button" className="primary small" onClick={onReady}>Return to my workspace</button>}
    </div>
  </div>;
}

export function ConnectionRecovery() {
  return <main className="connection-recovery"><span className="brand-mark">f</span>
    <h1>Let’s reconnect your workspace.</h1>
    <p>Folio couldn’t restore your session because the service was unavailable. Your sign-in information is still saved on this device.</p>
    <ConnectionStatus onReady={() => window.location.reload()} />
  </main>;
}
