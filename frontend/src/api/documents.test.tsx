import { afterEach, expect, test, vi } from 'vitest';
import { uploadDocument } from './documents';
import { ApiError, setRefreshHandler } from './client';
afterEach(() => { vi.unstubAllGlobals(); setRefreshHandler(null); });
test('upload settles with an error when token refresh fails during an outage', async () => {
  class FailedUpload {
    status=401;
    upload={onprogress:null};
    onload?: () => Promise<void>;
    open() {}
    setRequestHeader() {}
    send() { queueMicrotask(() => { void this.onload?.(); }); }
  }
  vi.stubGlobal('XMLHttpRequest',FailedUpload);
  setRefreshHandler(async () => { throw new ApiError(503,'Temporarily unavailable'); });
  await expect(uploadDocument(new File(['fixture'],'fixture.txt'))).rejects.toThrow('Temporarily unavailable');
});
