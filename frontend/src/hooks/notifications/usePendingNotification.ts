import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

const notificationIdPattern = /^\d+$/;

function readNotificationId(value: string | null) {
  return value && notificationIdPattern.test(value) ? value : null;
}

export function usePendingNotification() {
  const [searchParams] = useSearchParams();
  const [notificationId, setNotificationId] = useState(() =>
    readNotificationId(searchParams.get("notificationId")),
  );

  useEffect(() => {
    const receive = (event: MessageEvent) => {
      const nextNotificationId = String(event.data?.notificationId);
      if (event.data?.type === "PUSH_RECEIVED" && notificationIdPattern.test(nextNotificationId)) {
        setNotificationId(nextNotificationId);
      }
    };

    navigator.serviceWorker?.addEventListener("message", receive);
    return () => navigator.serviceWorker?.removeEventListener("message", receive);
  }, []);

  const clearNotification = useCallback(() => {
    setNotificationId(null);
    const url = new URL(window.location.href);
    url.searchParams.delete("notificationId");
    url.searchParams.delete("answer");
    window.history.replaceState(null, "", url);
  }, []);

  return { notificationId, clearNotification };
}
