import Brand from "../common/Brand";

export default function UserHeader({ onLogout }: { onLogout: () => void }) {
  return (
    <header className="mx-auto flex max-w-xl items-center justify-between px-5 py-5">
      <Brand />
      <button className="user-quiet-action" onClick={onLogout}>
        로그아웃
      </button>
    </header>
  );
}
