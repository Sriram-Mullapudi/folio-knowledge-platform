import { Fragment, useEffect, useRef, useState } from "react";
import type { KeyboardEvent, ReactNode } from "react";
import { streamAnswer, type ChatSource } from "../api/chat";

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  sources?: ChatSource[];
  streaming?: boolean;
  error?: string;
}

function newId(): string {
  return crypto.randomUUID();
}

// Wrap [n] citation markers in a styled span so they stand out and tie back to
// the source panel. Everything else renders as plain text.
function renderWithCitations(content: string): ReactNode[] {
  return content.split(/(\[\d+\])/g).map((part, i) => {
    if (/^\[\d+\]$/.test(part)) {
      return (
        <span key={i} className="mx-0.5 rounded bg-signal/15 px-1 font-semibold text-signal">
          {part}
        </span>
      );
    }
    return <Fragment key={i}>{part}</Fragment>;
  });
}

function MessageBubble({ message }: { message: Message }) {
  const isUser = message.role === "user";
  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[85%] rounded-lg px-4 py-3 font-mono text-sm leading-relaxed ${
          isUser ? "bg-signal/15 text-zinc-100" : "border border-line bg-panel/50 text-zinc-200"
        }`}
      >
        {message.error ? (
          <span className="text-down">{message.error}</span>
        ) : (
          <span className="whitespace-pre-wrap break-words">
            {renderWithCitations(message.content)}
            {message.streaming && (
              <span className="ml-0.5 inline-block h-3.5 w-1.5 animate-pulse bg-signal align-middle" />
            )}
          </span>
        )}
        {!isUser && !message.streaming && (message.sources?.length ?? 0) > 0 && (
          <p className="mt-2 font-mono text-[10px] uppercase tracking-widest text-muted">
            {message.sources!.length} source{message.sources!.length === 1 ? "" : "s"} cited
          </p>
        )}
      </div>
    </div>
  );
}

function SourcePanel({ sources }: { sources: ChatSource[] }) {
  const [expanded, setExpanded] = useState<number | null>(null);

  if (sources.length === 0) {
    return (
      <p className="font-mono text-xs text-muted">
        Sources for the answer will appear here, each tied to a [n] citation.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {sources.map((s) => {
        const open = expanded === s.number;
        const text = open || s.snippet.length <= 220 ? s.snippet : `${s.snippet.slice(0, 220)}…`;
        return (
          <div key={s.number} className="rounded-lg border border-line bg-panel/40 p-3">
            <div className="flex items-center justify-between font-mono text-[11px]">
              <span className="font-semibold text-signal">[{s.number}]</span>
              <span className="text-muted">retrieved passage</span>
            </div>
            <p className="mt-1 truncate font-mono text-xs text-zinc-300" title={s.filename}>
              {s.filename}
            </p>
            <p className="font-mono text-[10px] text-muted">chunk {s.chunkIndex}</p>
            <p className="mt-2 whitespace-pre-wrap break-words font-mono text-[11px] leading-relaxed text-zinc-400">
              {text}
            </p>
            {s.snippet.length > 220 && (
              <button
                onClick={() => setExpanded(open ? null : s.number)}
                className="mt-1 font-mono text-[10px] text-signal/80 hover:text-signal"
              >
                {open ? "show less" : "show more"}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

export function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [panelSources, setPanelSources] = useState<ChatSource[]>([]);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const patch = (id: string, fn: (m: Message) => Message) =>
    setMessages((prev) => prev.map((m) => (m.id === id ? fn(m) : m)));

  const send = () => {
    const question = input.trim();
    if (!question || streaming) return;

    const assistantId = newId();
    setMessages((prev) => [
      ...prev,
      { id: newId(), role: "user", content: question },
      { id: assistantId, role: "assistant", content: "", streaming: true },
    ]);
    setInput("");
    setStreaming(true);
    setPanelSources([]);

    void streamAnswer(
      question,
      {},
      {
        onSources: (sources) => {
          setPanelSources(sources);
          patch(assistantId, (m) => ({ ...m, sources }));
        },
        onToken: (delta) => patch(assistantId, (m) => ({ ...m, content: m.content + delta })),
        onDone: () => {
          patch(assistantId, (m) => ({ ...m, streaming: false }));
          setStreaming(false);
        },
        onError: (msg) => {
          patch(assistantId, (m) => ({ ...m, streaming: false, error: msg }));
          setStreaming(false);
        },
      },
    );
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  return (
    <div className="flex flex-col gap-6 md:flex-row">
      {/* Conversation */}
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex h-[60vh] flex-col gap-4 overflow-y-auto rounded-lg border border-line bg-ink/30 p-4">
          {messages.length === 0 ? (
            <div className="flex h-full items-center justify-center text-center">
              <p className="max-w-sm font-mono text-xs leading-relaxed text-muted">
                Ask a question about your documents. Answers are grounded in your uploaded
                content and cite their sources with [n] markers.
              </p>
            </div>
          ) : (
            messages.map((m) => <MessageBubble key={m.id} message={m} />)
          )}
          <div ref={bottomRef} />
        </div>

        <div className="mt-4 flex items-end gap-3">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={onKeyDown}
            rows={2}
            placeholder="Ask anything about your documents…"
            className="min-h-[44px] flex-1 resize-none rounded-md border border-line bg-panel/40 px-3 py-2
                       font-mono text-sm text-zinc-100 placeholder:text-muted
                       focus:border-signal/50 focus:outline-none"
          />
          <button
            onClick={send}
            disabled={streaming || input.trim().length === 0}
            className="rounded-md bg-signal px-4 py-2 font-mono text-xs font-semibold text-ink
                       transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {streaming ? "…" : "ask"}
          </button>
        </div>
      </div>

      {/* Source panel */}
      <aside className="w-full shrink-0 md:w-72">
        <h2 className="mb-3 font-mono text-xs uppercase tracking-widest text-muted">Sources</h2>
        <SourcePanel sources={panelSources} />
      </aside>
    </div>
  );
}
