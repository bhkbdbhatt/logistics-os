import { useState } from 'react';
import { useAuth } from '../auth/AuthProvider';

export function LoginPage() {
  const { mode, loginLocal, loginOidc, error, busy } = useAuth();
  const [username, setUsername] = useState('ops');
  const [password, setPassword] = useState('ops');

  if (mode === 'oidc') {
    return (
      <div className="login">
        <h1>Logistics OS</h1>
        <p>Sign in with your organisation account.</p>
        <button type="button" disabled={busy} onClick={() => void loginOidc()}>
          {busy ? 'Redirecting…' : 'Sign in'}
        </button>
        {error ? <p className="error">{error}</p> : null}
      </div>
    );
  }

  return (
    <div className="login">
      <h1>Logistics OS</h1>
      <p>Local development sign-in.</p>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void loginLocal(username, password);
        }}
      >
        <label>
          Username
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      {error ? <p className="error">{error}</p> : null}
    </div>
  );
}
