type StaffHeaderProps = {
  title?: string;
  connection: "connecting" | "connected" | "disconnected";
  refreshing: boolean;
  onRefresh: () => void;
};

export default function StaffHeader({
  title,
  connection,
  refreshing,
  onRefresh,
}: StaffHeaderProps) {
  const connected = connection === "connected";

  return (
    <header className="sticky top-0 z-10 flex min-h-16 items-center justify-between border-b border-stone-200 bg-white/90 px-5 backdrop-blur md:px-8">
      <strong>{title}</strong>
      <div className="flex items-center gap-3">
        <span
          className="hidden size-9 items-center justify-center rounded-full bg-stone-100 sm:flex"
          aria-label={connected ? "실시간 연결됨" : "실시간 연결 확인 중"}
          title={connected ? "실시간 연결됨" : "실시간 연결 확인 중"}
          role="status"
        >
          <i
            className={`size-2.5 rounded-full ring-4 ${connected ? "bg-emerald-500 ring-emerald-100" : "bg-amber-500 ring-amber-100"}`}
          />
        </span>
        <button
          className="rounded-lg border border-stone-300 px-3 py-2 text-sm font-semibold hover:bg-stone-50"
          disabled={refreshing}
          onClick={onRefresh}
        >
          {refreshing ? "갱신 중…" : "새로고침"}
        </button>
      </div>
    </header>
  );
}
