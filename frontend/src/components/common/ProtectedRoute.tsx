import type { ReactNode } from "react";
import { Outlet } from "react-router-dom";
import { useSession } from "../../hooks/useSession";

export default function ProtectedRoute({ fallback }: { fallback: ReactNode }) {
  return useSession() ? <Outlet /> : fallback;
}
