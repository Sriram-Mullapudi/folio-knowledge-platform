import { Fragment, useCallback, useEffect, useState } from "react";
import type { ChangeEvent, DragEvent } from "react";
import {
  deleteDocument,
  downloadDocument,
  listChunks,
  listDocuments,
  reprocessDocument,
  uploadDocument,
  type ChunkItem,
  type DocumentItem,
} from "../api/documents";
import { ApiError } from "../api/client";

const PAGE_SIZE = 10;
const ACCEPT = ".pdf,.txt,.md,.markdown";
// While anything is mid-pipeline, re-poll the list so status flips live.
const POLL_MS = 2500;

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  const units = ["KB", "MB", "GB"];
  let value = n / 1024;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  return `${value.toFixed(1)} ${units[i]}`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

function isInFlight(status: string): boolean {
  return status === "UPLOADED" || status === "PROCESSING";
}

function StatusBadge({ status, chunkCount }: { status: string; chunkCount: number | null }) {
  if (status === "PROCESSING") {
    return (
      <span className="inline-flex items-center gap-1.5 text-warn">
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-warn" />
        processing
      </span>
    );
  }
  if (status === "COMPLETED") {
    return (
      <span className="text-up">
        completed
        {typeof chunkCount === "number" ? ` · ${chunkCount} chunks` : ""}
      </span>
    );
  }
  if (status === "FAILED") {
    return <span className="text-down">failed</span>;
  }
  return (
    <span className="inline-flex items-center gap-1.5 text-muted">
      <span className="h-1.5 w-1.5 rounded-full bg-muted" />
      queued
    </span>
  );
}

export function DocumentsPage() {
  const [docs, setDocs] = useState<DocumentItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [listError, setListError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const [selected, setSelected] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadOk, setUploadOk] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [chunks, setChunks] = useState<ChunkItem[]>([]);
  const [chunksLoading, setChunksLoading] = useState(false);

  const load = useCallback(async (p: number) => {
    setLoading(true);
    setListError(null);
    try {
      const res = await listDocuments(p, PAGE_SIZE);
      setDocs(res.content);
      setPage(res.page);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : "Failed to load documents");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(0);
  }, [load]);

  // Poll while any document is still being processed, then stop on its own.
  useEffect(() => {
    if (!docs.some((d) => isInFlight(d.status))) return;
    const timer = setTimeout(() => load(page), POLL_MS);
    return () => clearTimeout(timer);
  }, [docs, page, load]);

  const pickFile = (file: File | null) => {
    setSelected(file);
    setUploadError(null);
    setUploadOk(null);
    setProgress(0);
  };

  const onInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    pickFile(e.target.files?.[0] ?? null);
  };

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragging(false);
    pickFile(e.dataTransfer.files?.[0] ?? null);
  };

  const doUpload = async () => {
    if (!selected) return;
    setUploading(true);
    setUploadError(null);
    setUploadOk(null);
    setProgress(0);
    try {
      const created = await uploadDocument(selected, setProgress);
      setUploadOk(`Uploaded ${created.filename}`);
      setSelected(null);
      await load(0);
    } catch (err) {
      setUploadError(err instanceof ApiError ? err.message : "Upload failed");
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (doc: DocumentItem) => {
    try {
      await downloadDocument(doc.id, doc.filename);
    } catch {
      setListError("Download failed");
    }
  };

  const handleDelete = async (doc: DocumentItem) => {
    if (!window.confirm(`Delete "${doc.filename}"? This cannot be undone.`)) return;
    setBusyId(doc.id);
    try {
      await deleteDocument(doc.id);
      // Reload current page, stepping back if we removed the last item on it.
      const target = docs.length === 1 && page > 0 ? page - 1 : page;
      if (expandedId === doc.id) setExpandedId(null);
      await load(target);
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : "Delete failed");
    } finally {
      setBusyId(null);
    }
  };

  const handleReprocess = async (doc: DocumentItem) => {
    setBusyId(doc.id);
    try {
      await reprocessDocument(doc.id);
      await load(page);
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : "Reprocess failed");
    } finally {
      setBusyId(null);
    }
  };

  const toggleChunks = async (doc: DocumentItem) => {
    if (expandedId === doc.id) {
      setExpandedId(null);
      setChunks([]);
      return;
    }
    setExpandedId(doc.id);
    setChunks([]);
    setChunksLoading(true);
    try {
      const res = await listChunks(doc.id, 0, 50);
      setChunks(res.content);
    } catch {
      setListError("Failed to load chunks");
      setExpandedId(null);
    } finally {
      setChunksLoading(false);
    }
  };

  return (
    <div className="space-y-8">
      {/* Upload panel */}
      <section
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={`rounded-lg border border-dashed p-8 text-center transition-colors ${
          dragging ? "border-signal bg-signal/5" : "border-line bg-panel/40"
        }`}
      >
        <p className="font-mono text-sm text-zinc-300">
          Drag a file here, or{" "}
          <label className="cursor-pointer text-signal underline-offset-2 hover:underline">
            browse
            <input type="file" accept={ACCEPT} className="hidden" onChange={onInputChange} />
          </label>
        </p>
        <p className="mt-2 font-mono text-[11px] text-muted">PDF · TXT · Markdown · up to 25&nbsp;MB</p>

        {selected && (
          <div className="mx-auto mt-5 max-w-md text-left">
            <div className="flex items-center justify-between font-mono text-xs text-zinc-300">
              <span className="truncate">{selected.name}</span>
              <span className="text-muted">{formatBytes(selected.size)}</span>
            </div>

            {uploading && (
              <div className="mt-3 h-1.5 w-full overflow-hidden rounded bg-line">
                <div
                  className="h-full bg-signal transition-all"
                  style={{ width: `${progress}%` }}
                />
              </div>
            )}

            <div className="mt-4 flex gap-3">
              <button
                onClick={doUpload}
                disabled={uploading}
                className="rounded-md bg-signal px-4 py-2 font-mono text-xs font-semibold text-ink
                           transition-opacity hover:opacity-90 disabled:opacity-50"
              >
                {uploading ? `uploading… ${progress}%` : "upload"}
              </button>
              {!uploading && (
                <button
                  onClick={() => pickFile(null)}
                  className="rounded-md border border-line px-4 py-2 font-mono text-xs text-muted
                             transition-colors hover:text-zinc-200"
                >
                  clear
                </button>
              )}
            </div>
          </div>
        )}

        {uploadError && (
          <p role="alert" className="mt-4 font-mono text-xs text-down">
            {uploadError}
          </p>
        )}
        {uploadOk && <p className="mt-4 font-mono text-xs text-up">{uploadOk}</p>}
      </section>

      {/* History */}
      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-mono text-xs uppercase tracking-widest text-muted">
            Upload history
          </h2>
          <span className="font-mono text-[11px] text-muted">{totalElements} total</span>
        </div>

        {listError && <p className="font-mono text-xs text-down">{listError}</p>}

        <div className="overflow-hidden rounded-lg border border-line">
          <table className="w-full text-left">
            <thead className="bg-panel/60">
              <tr className="font-mono text-[10px] uppercase tracking-widest text-muted">
                <th className="px-4 py-3">File</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Size</th>
                <th className="px-4 py-3">Uploaded</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="font-mono text-xs text-zinc-300">
              {docs.length === 0 && !loading && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-muted">
                    No documents yet.
                  </td>
                </tr>
              )}
              {docs.map((doc) => (
                <Fragment key={doc.id}>
                  <tr className="border-t border-line/70">
                    <td className="max-w-[14rem] truncate px-4 py-3" title={doc.filename}>
                      {doc.filename}
                    </td>
                    <td className="px-4 py-3 text-muted">{doc.contentType}</td>
                    <td className="px-4 py-3 text-muted">{formatBytes(doc.sizeBytes)}</td>
                    <td className="px-4 py-3 text-muted">{formatDate(doc.uploadedAt)}</td>
                    <td className="px-4 py-3" title={doc.processingError ?? undefined}>
                      <StatusBadge status={doc.status} chunkCount={doc.chunkCount} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      {doc.status === "COMPLETED" && (doc.chunkCount ?? 0) > 0 && (
                        <button
                          onClick={() => toggleChunks(doc)}
                          className="text-signal/80 transition-colors hover:text-signal"
                        >
                          {expandedId === doc.id ? "hide" : "chunks"}
                        </button>
                      )}
                      {doc.status === "FAILED" && (
                        <button
                          onClick={() => handleReprocess(doc)}
                          disabled={busyId === doc.id}
                          className="text-warn/90 transition-colors hover:text-warn disabled:opacity-50"
                        >
                          {busyId === doc.id ? "…" : "reprocess"}
                        </button>
                      )}
                      <button
                        onClick={() => handleDownload(doc)}
                        className="ml-4 text-signal/80 transition-colors hover:text-signal"
                      >
                        download
                      </button>
                      <button
                        onClick={() => handleDelete(doc)}
                        disabled={busyId === doc.id}
                        className="ml-4 text-muted transition-colors hover:text-down disabled:opacity-50"
                      >
                        {busyId === doc.id ? "…" : "delete"}
                      </button>
                    </td>
                  </tr>
                  {expandedId === doc.id && (
                    <tr className="border-t border-line/70 bg-ink/40">
                      <td colSpan={6} className="px-4 py-4">
                        {chunksLoading ? (
                          <p className="text-muted">loading chunks…</p>
                        ) : (
                          <div className="space-y-3">
                            <p className="text-[11px] uppercase tracking-widest text-muted">
                              Showing {chunks.length} chunk{chunks.length === 1 ? "" : "s"}
                              {(doc.chunkCount ?? 0) > chunks.length
                                ? ` of ${doc.chunkCount}`
                                : ""}
                            </p>
                            <div className="max-h-80 space-y-2 overflow-y-auto pr-1">
                              {chunks.map((c) => (
                                <div
                                  key={c.id}
                                  className="rounded border border-line/70 bg-panel/40 p-3"
                                >
                                  <div className="mb-1 flex justify-between text-[10px] text-muted">
                                    <span>chunk #{c.index}</span>
                                    <span>
                                      {c.charCount} chars · ~{c.tokenEstimate} tokens
                                    </span>
                                  </div>
                                  <p className="whitespace-pre-wrap break-words text-zinc-300">
                                    {c.content}
                                  </p>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between font-mono text-xs text-muted">
            <button
              onClick={() => load(page - 1)}
              disabled={page <= 0 || loading}
              className="rounded border border-line px-3 py-1.5 transition-colors
                         hover:text-zinc-200 disabled:opacity-40"
            >
              ← prev
            </button>
            <span>
              page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => load(page + 1)}
              disabled={page >= totalPages - 1 || loading}
              className="rounded border border-line px-3 py-1.5 transition-colors
                         hover:text-zinc-200 disabled:opacity-40"
            >
              next →
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
