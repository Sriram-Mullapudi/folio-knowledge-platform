import { useEffect, useState } from "react";
import { apiGet, apiPost } from "../api/client";
import { listDocuments, type DocumentItem } from "../api/documents";
import type { ChatSource } from "../api/chat";

type Summary = { documents: number; ready: number; failed: number; processing: number; passages: number; bytes: number; searchMode: string; answersEnabled: boolean };
type Hit = { chunkId: string; documentId: string; chunkIndex: number; content: string };
type Answer = { mode: string; answer: string; sources: ChatSource[]; trace: string[] };
const field = "w-full rounded-lg border border-line bg-panel p-3 text-sm text-zinc-100";
const button = "rounded-lg bg-signal px-4 py-2 text-sm font-semibold text-black disabled:opacity-40";

export function WorkspacePage() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [query, setQuery] = useState("");
  const [documentId, setDocumentId] = useState("");
  const [mode, setMode] = useState("AUTO");
  const [hits, setHits] = useState<Hit[]>([]);
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [searched, setSearched] = useState(false);
  const refresh = async () => {
    try {
      const [stats, page] = await Promise.all([apiGet<Summary>("/api/v1/workspace/summary"), listDocuments(0,100)]);
      setSummary(stats); setDocuments(page.content);
    } catch (e) { setError(e instanceof Error ? e.message : "Could not load workspace"); }
  };
  useEffect(() => { void refresh(); }, []);
  const run = async (kind: "search" | "agent") => {
    if (!query.trim() || busy) return;
    setBusy(true); setError(""); setAnswer(null); setHits([]); setSearched(false);
    try {
      if (kind === "search") {
        setHits(await apiPost<Hit[]>("/api/v1/search", { query, documentId: documentId || null, k: 8 }));
        setSearched(true);
      } else setAnswer(await apiPost<Answer>("/api/v1/agent", { question: query, mode, documentId: documentId || null }, 65000));
    } catch (e) { setError(e instanceof Error ? e.message : "Request failed"); }
    finally { setBusy(false); }
  };
  return <div className="space-y-6">
    <div className="flex items-center justify-between"><div><h2 className="text-2xl font-semibold">Your knowledge workspace</h2><p className="mt-2 text-sm text-muted">Find evidence, explore questions, and inspect the sources.</p></div><button className="text-sm text-signal" onClick={() => void refresh()}>Refresh</button></div>
    {summary && <>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">{[["Documents",summary.documents],["Ready",summary.ready],["Processing",summary.processing],["Needs attention",summary.failed]].map(([label,value]) => <div key={label} className="rounded-xl border border-line bg-panel p-4"><p className="text-xs text-muted">{label}</p><p className="mt-2 text-3xl">{value}</p></div>)}</div>
      <p className="text-sm text-muted">{summary.passages} passages · {(summary.bytes/1024/1024).toFixed(1)} MB · {summary.searchMode === "hybrid" ? "Keyword + semantic search" : "Keyword search available"}. {summary.answersEnabled ? "AI answers enabled." : "AI answers need a generation provider. Search and document counts work without one."}</p>
    </>}
    <section className="space-y-4 rounded-xl border border-line bg-panel/50 p-5">
      <label className="block text-sm">Question or search terms<textarea className={field+" mt-2"} rows={3} maxLength={4000} value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search a policy, compare topics, or ask how many documents you have" /></label>
      <div className="grid gap-4 md:grid-cols-2"><label className="text-sm">Document<select className={field+" mt-2"} value={documentId} onChange={e=>setDocumentId(e.target.value)}><option value="">All my documents</option>{documents.map(d=><option key={d.id} value={d.id}>{d.filename}</option>)}</select></label><label className="text-sm">Answer workflow<select className={field+" mt-2"} value={mode} onChange={e=>setMode(e.target.value)}><option value="AUTO">Choose automatically</option><option value="RETRIEVAL">Answer from sources</option><option value="RESEARCH">Research / compare</option><option value="SUMMARIZATION">Summarize a document</option><option value="METADATA">Document inventory</option></select></label></div>
      <p className="text-xs text-muted">Choose a document for summaries. Summaries cover up to the first 20 passages within the context limit. The selector shows your latest 100 documents.</p>
      <div className="flex gap-3"><button className={button} disabled={busy || !query.trim()} onClick={()=>void run("search")}>Search passages</button><button className={button} disabled={busy || !query.trim()} onClick={()=>void run("agent")}>{busy ? "Working…" : "Run workflow"}</button></div>
    </section>
    {error && <p role="alert" className="rounded-lg border border-down p-4 text-down">{error}</p>}
    {searched && hits.length===0 && <p role="status">No matching passages. Try fewer keywords or another document.</p>}
    {hits.map(h=><article className="rounded-xl border border-line p-4" key={h.chunkId}><h3 className="text-sm text-signal">{documents.find(d=>d.id===h.documentId)?.filename ?? "Document"} · passage {h.chunkIndex+1}</h3><p className="mt-2 whitespace-pre-wrap break-words text-sm">{h.content}</p></article>)}
    {answer && <section className="space-y-4 rounded-xl border border-line p-5"><h3 className="text-sm uppercase text-signal">{answer.mode.toLowerCase()}</h3><p className="whitespace-pre-wrap break-words">{answer.answer}</p><details><summary className="cursor-pointer text-sm text-muted">How this answer was prepared</summary><ol className="mt-3 list-inside list-decimal text-sm">{answer.trace.map((s,i)=><li key={i}>{s}</li>)}</ol></details>{answer.sources.map(s=><details key={s.number} className="rounded-lg border border-line p-3"><summary className="cursor-pointer text-sm text-signal">[{s.number}] {s.filename} · passage {s.chunkIndex+1}</summary><p className="mt-2 whitespace-pre-wrap break-words text-sm text-muted">{s.snippet}</p></details>)}</section>}
  </div>;
}
