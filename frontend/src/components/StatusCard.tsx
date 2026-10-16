export type Health = "up" | "down" | "warn" | "unknown";

interface StatusCardProps {
  label: string;
  detail: string;
  health: Health;
  index: number;
  href?: string;
}

const DOT: Record<Health, string> = {
  up: "bg-up",
  down: "bg-down",
  warn: "bg-warn",
  unknown: "bg-muted",
};

const LABEL: Record<Health, string> = {
  up: "OPERATIONAL",
  down: "DOWN",
  warn: "DEGRADED",
  unknown: "UNKNOWN",
};

export function StatusCard({ label, detail, health, index, href }: StatusCardProps) {
  const body = (
    <div
      className="group rounded-lg border border-line bg-panel/70 p-5 backdrop-blur-sm
                 transition-colors hover:border-signal/40 animate-riseIn"
      style={{ animationDelay: `${index * 80}ms` }}
    >
      <div className="flex items-center justify-between">
        <span className="font-mono text-xs uppercase tracking-widest text-muted">
          {label}
        </span>
        <span className="flex items-center gap-2">
          <span
            className={`h-2 w-2 rounded-full ${DOT[health]} ${
              health === "up" ? "animate-pulseDot" : ""
            }`}
          />
          <span
            className={`font-mono text-[10px] tracking-wider ${
              health === "up"
                ? "text-up"
                : health === "down"
                  ? "text-down"
                  : health === "warn"
                    ? "text-warn"
                    : "text-muted"
            }`}
          >
            {LABEL[health]}
          </span>
        </span>
      </div>
      <p className="mt-3 font-mono text-sm text-zinc-300">{detail}</p>
      {href && (
        <span className="mt-3 inline-block font-mono text-xs text-signal/80 group-hover:text-signal">
          open ↗
        </span>
      )}
    </div>
  );

  return href ? (
    <a href={href} target="_blank" rel="noreferrer" className="block">
      {body}
    </a>
  ) : (
    body
  );
}
