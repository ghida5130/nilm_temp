import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getApiErrorMessage } from "../../api/client";
import type { SubjectsResponse } from "../../api/monitoring";
import { readSubjectStream } from "../../api/stream";
import type { StatusEvent } from "../../types/monitoring";
import { monitoringKeys } from "../api/monitoringKeys";

export function useSubjectStream() {
  const queryClient = useQueryClient();
  const [connection, setConnection] = useState<"connecting" | "connected" | "disconnected">(
    "connecting",
  );
  const [error, setError] = useState("");
  const [notice, setNotice] = useState<StatusEvent | null>(null);

  useEffect(() => {
    let active = true;
    let retries = 0;
    let timer: number | undefined;
    let controller: AbortController | undefined;

    const connect = async () => {
      controller = new AbortController();

      try {
        await readSubjectStream(
          controller.signal,
          () => {
            if (!active) return;
            retries = 0;
            setConnection("connected");
            setError("");
            queryClient.invalidateQueries({ queryKey: monitoringKeys.subjects }).catch((cause) => {
              if (active) setError(getApiErrorMessage(cause));
            });
          },
          (eventName, payload) => {
            if (!active || eventName !== "subject-status") return;
            const event = payload as StatusEvent;
            queryClient.setQueryData<SubjectsResponse>(monitoringKeys.subjects, (current) =>
              current
                ? {
                    subjects: current.subjects.map((subject) =>
                      subject.subjectId === event.subjectId && event.version > subject.version
                        ? { ...subject, ...event }
                        : subject,
                    ),
                  }
                : current,
            );
            if (event.riskLevel === "DANGER" || event.trigger === "SUBJECT_RESPONSE")
              setNotice(event);
          },
        );
      } catch (cause) {
        if (active) setError(getApiErrorMessage(cause));
      }

      if (active) {
        setConnection("disconnected");
        timer = window.setTimeout(
          () => connect().catch(() => undefined),
          Math.min(30_000, 1_000 * 2 ** Math.min(retries++, 5)),
        );
      }
    };

    connect().catch(() => undefined);

    return () => {
      active = false;
      controller?.abort();
      window.clearTimeout(timer);
    };
  }, [queryClient]);

  return { connection, error, notice, setNotice };
}
