import { afterEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { ConnectionStatus } from './ConnectionStatus';
import { apiGet } from '../api/client';
vi.mock('../api/client', () => ({ apiGet: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });
test('offline recovery explains the action and rechecks successfully', async () => {
  vi.mocked(apiGet).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({status:'UP'});
  const resume=vi.fn();
  render(<ConnectionStatus onReady={resume} />);
  await screen.findByText('Your workspace is offline');
  expect(screen.getByText('Start Folio')).toBeTruthy();
  expect(screen.queryByRole('button',{name:'Return to my workspace'})).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'Check again'}));
  await screen.findByText('Your workspace is ready');
  fireEvent.click(screen.getByRole('button',{name:'Return to my workspace'}));
  expect(resume).toHaveBeenCalledOnce();
});
test('a reachable but unhealthy service is not presented as ready', async () => {
  vi.mocked(apiGet).mockResolvedValue({status:'DOWN'});
  render(<ConnectionStatus />);
  await screen.findByText('Your workspace is offline');
});
