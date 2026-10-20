import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from './AuthContext';
import { ApiError, forceRefresh, setAccessToken } from '../api/client';
import * as authApi from '../api/auth';
vi.mock('../api/auth', () => ({ refresh: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn() }));
function Probe() { const auth=useAuth(); return <div>{auth.status}</div>; }
beforeEach(() => { localStorage.clear(); localStorage.setItem('ekp.refreshToken','saved-session'); vi.resetAllMocks(); setAccessToken(null); });
afterEach(cleanup);
test('an outage preserves the saved session and shows recovery', async () => {
  vi.mocked(authApi.refresh).mockRejectedValue(new ApiError(0,'offline'));
  render(<AuthProvider><Probe /></AuthProvider>);
  expect(await screen.findByText('offline')).toBeTruthy();
  expect(localStorage.getItem('ekp.refreshToken')).toBe('saved-session');
});
test('a rejected refresh clears an invalid saved session', async () => {
  vi.mocked(authApi.refresh).mockRejectedValue(new ApiError(401,'expired'));
  render(<AuthProvider><Probe /></AuthProvider>);
  expect(await screen.findByText('anon')).toBeTruthy();
  expect(localStorage.getItem('ekp.refreshToken')).toBeNull();
});
test('an in-session outage does not unmount the signed-in workspace', async () => {
  vi.mocked(authApi.refresh).mockResolvedValue({accessToken:'access',refreshToken:'rotated',tokenType:'Bearer',expiresInSeconds:900,user:{id:'fixture',email:'fixture@example.test',role:'USER',enabled:true,createdAt:'2026-01-01'}});
  render(<AuthProvider><Probe /></AuthProvider>);
  await screen.findByText('authed');
  vi.mocked(authApi.refresh).mockRejectedValue(new ApiError(503,'offline'));
  await expect(forceRefresh()).rejects.toThrow('offline');
  await waitFor(() => expect(screen.getByText('authed')).toBeTruthy());
  expect(localStorage.getItem('ekp.refreshToken')).toBe('rotated');
});
