import { useSyncExternalStore } from "react";
import { hasSession, SESSION_EVENT } from "../api/tokenStorage";

function subscribe(callback: () => void) {
  window.addEventListener(SESSION_EVENT, callback);
  window.addEventListener("storage", callback);
  return () => {
    window.removeEventListener(SESSION_EVENT, callback);
    window.removeEventListener("storage", callback);
  };
}

export function useSession() {
  return useSyncExternalStore(subscribe, hasSession, () => false);
}
