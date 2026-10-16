import { apiUrl, forceRefresh, getAccessToken } from "./client";

export interface ChatSource {
  number: number;
  documentId: string;
  filename: string;
  chunkIndex: number;
  similarity: number;
  snippet: string;
}

export interface StreamHandlers {
  onSources: (sources: ChatSource[]) => void;
  onToken: (delta: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
}

export interface AskOptions {
  documentId?: string;
  collectionId?: string;
  k?: number;
  signal?: AbortSignal;
}

/**
 * Streams an answer over Server-Sent Events. The backend emits named JSON
 * events: `sources`, `token` ({delta}), `done`, and `error` ({message}).
 * Transparently refreshes the access token once on a 401, mirroring the
 * fetch client.
 */
export async function streamAnswer(
  question: string,
  opts: AskOptions,
  handlers: StreamHandlers,
): Promise<void> {
  let token = getAccessToken() ?? (await forceRefresh());
  if (!token) {
    handlers.onError("You are not signed in.");
    return;
  }

  const run = async (bearer: string): Promise<Response> =>
    fetch(apiUrl("/api/v1/chat"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream, application/json",
        Authorization: `Bearer ${bearer}`,
      },
      body: JSON.stringify({
        question,
        documentId: opts.documentId ?? null,
        collectionId: opts.collectionId ?? null,
        k: opts.k ?? null,
      }),
      signal: opts.signal,
    });

  try {
    let res = await run(token);

    if (res.status === 401) {
      const refreshed = await forceRefresh();
      if (!refreshed) {
        handlers.onError("Your session expired. Please sign in again.");
        return;
      }
      token = refreshed;
      res = await run(token);
    }

    if (!res.ok) {
      handlers.onError(await errorMessage(res));
      return;
    }
    if (!res.body) {
      handlers.onError("The server returned no response stream.");
      return;
    }

    let finished = false;
    await consume(res.body, {
      ...handlers,
      onDone: () => { finished = true; handlers.onDone(); },
      onError: (message) => { finished = true; handlers.onError(message); },
    });
    if (!finished) handlers.onError("The answer stopped before it finished. Please try again.");
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") {
      return; // caller cancelled; not an error
    }
    handlers.onError("Connection to the server was lost.");
  }
}

async function errorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json();
    if (body && typeof body.message === "string") return body.message;
  } catch {
    /* not JSON */
  }
  if (res.status === 503) return "Answers are unavailable — generation is not configured.";
  return `Request failed (${res.status}).`;
}

async function consume(body: ReadableStream<Uint8Array>, handlers: StreamHandlers): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, "\n");

    let sep = buffer.indexOf("\n\n");
    while (sep >= 0) {
      handleFrame(buffer.slice(0, sep), handlers);
      buffer = buffer.slice(sep + 2);
      sep = buffer.indexOf("\n\n");
    }
  }
  if (buffer.trim()) handleFrame(buffer, handlers);
}

function handleFrame(frame: string, handlers: StreamHandlers): void {
  let event = "message";
  const dataParts: string[] = [];
  for (const line of frame.split("\n")) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataParts.push(line.slice(5).replace(/^ /, ""));
    }
  }
  if (dataParts.length === 0) return;

  try {
    const data = JSON.parse(dataParts.join("\n"));
    switch (event) {
      case "sources":
        handlers.onSources(data as ChatSource[]);
        break;
      case "token":
        handlers.onToken((data as { delta: string }).delta);
        break;
      case "done":
        handlers.onDone();
        break;
      case "error":
        handlers.onError((data as { message?: string }).message ?? "Generation failed.");
        break;
      default:
        break;
    }
  } catch {
    /* ignore malformed frame */
  }
}
