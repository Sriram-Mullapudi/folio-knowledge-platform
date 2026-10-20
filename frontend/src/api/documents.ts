import {
  ApiError,
  apiDelete,
  apiGet,
  apiPost,
  apiUrl,
  forceRefresh,
  getAccessToken,
} from "./client";

export interface DocumentItem {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  status: string;
  chunkCount: number | null;
  uploadedAt: string;
  processedAt: string | null;
  processingError: string | null;
  uploadedBy: string;
  collectionId: string | null;
}

export interface ChunkItem {
  id: string;
  index: number;
  content: string;
  charCount: number;
  tokenEstimate: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function listDocuments(page = 0, size = 20, collectionId = ""): Promise<PageResponse<DocumentItem>> {
  return apiGet<PageResponse<DocumentItem>>(`/api/v1/documents?page=${page}&size=${size}${collectionId?`&collectionId=${encodeURIComponent(collectionId)}`:""}`);
}

export function deleteDocument(id: string): Promise<void> {
  return apiDelete<void>(`/api/v1/documents/${id}`);
}

export function listChunks(
  id: string,
  page = 0,
  size = 50,
): Promise<PageResponse<ChunkItem>> {
  return apiGet<PageResponse<ChunkItem>>(
    `/api/v1/documents/${id}/chunks?page=${page}&size=${size}`,
  );
}

export function reprocessDocument(id: string): Promise<DocumentItem> {
  return apiPost<DocumentItem>(`/api/v1/documents/${id}/reprocess`);
}

function parseXhrMessage(xhr: XMLHttpRequest): string {
  try {
    const body = JSON.parse(xhr.responseText);
    if (body && typeof body.message === "string") return body.message;
  } catch {
    /* not JSON */
  }
  return `Upload failed with status ${xhr.status}`;
}

/**
 * Uploads via XMLHttpRequest because fetch cannot report upload progress.
 * On a 401 it refreshes the access token once and retries, mirroring the
 * fetch client's behavior.
 */
export function uploadDocument(
  file: File,
  onProgress?: (percent: number) => void,
  retried = false,
): Promise<DocumentItem> {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append("file", file);

    const xhr = new XMLHttpRequest();
    xhr.open("POST", apiUrl("/api/v1/documents"));
    const token = getAccessToken();
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.setRequestHeader("X-Correlation-Id", crypto.randomUUID());
    xhr.timeout = 120_000;
    // Note: do NOT set Content-Type; the browser adds the multipart boundary.

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    };

    xhr.onload = async () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText) as DocumentItem);
        } catch {
          reject(new ApiError(xhr.status, "Malformed server response"));
        }
      } else if (xhr.status === 401 && !retried) {
        try {
          const newToken = await forceRefresh();
          if (newToken) {
            resolve(await uploadDocument(file, onProgress, true));
          } else {
            reject(new ApiError(401, "Session expired — please sign in again"));
          }
        } catch (err) {
          reject(err);
        }
      } else {
        reject(new ApiError(xhr.status, parseXhrMessage(xhr)));
      }
    };

    xhr.onerror = () => reject(new ApiError(0, "Network error: upload failed"));
    xhr.ontimeout = () => reject(new ApiError(0, "Upload timed out"));
    xhr.send(form);
  });
}

/** Fetches the file bytes (with auth) and triggers a browser download. */
export async function downloadDocument(id: string, filename: string): Promise<void> {
  const fetchOnce = (token: string | null) =>
    fetch(apiUrl(`/api/v1/documents/${id}/content`), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });

  let res = await fetchOnce(getAccessToken());
  if (res.status === 401) {
    const token = await forceRefresh();
    if (token) res = await fetchOnce(token);
  }
  if (!res.ok) throw new ApiError(res.status, "Download failed");

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
