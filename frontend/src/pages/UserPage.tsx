import { useState } from "react";
import "../styles/user.css";
import { useNavigate } from "react-router-dom";
import { getApiErrorMessage } from "../api/client";
import { clearSession } from "../api/tokenStorage";
import AwaySettings from "../components/user/AwaySettings";
import type { AwayDraft } from "../components/user/AwaySettings";
import NotificationPrompt from "../components/user/NotificationPrompt";
import PushNotificationCard from "../components/user/PushNotificationCard";
import UserDashboard from "../components/user/UserDashboard";
import UserHeader from "../components/user/UserHeader";
import UserNavigation from "../components/user/UserNavigation";
import type { UserTab } from "../components/user/UserNavigation";
import {
  useAwayModeMutation,
  useMyDashboardQuery,
  useNotificationResponseMutation,
} from "../hooks/api";
import { usePendingNotification } from "../hooks/notifications/usePendingNotification";
import { usePushSubscription } from "../hooks/notifications/usePushSubscription";

export default function UserPage({ comparison = false }: { comparison?: boolean }) {
  const navigate = useNavigate();
  const dashboard = useMyDashboardQuery();
  const awayMutation = useAwayModeMutation();
  const responseMutation = useNotificationResponseMutation();
  const [feedback, setFeedback] = useState("");
  const [tab, setTab] = useState<UserTab>("home");
  const [awayDraft, setAwayDraft] = useState<AwayDraft>({ duration: 60, start: "" });
  const { notificationId, clearNotification } = usePendingNotification();
  const pushSubscription = usePushSubscription();
  const data = dashboard.data;
  const busy = awayMutation.isPending || responseMutation.isPending;

  const updateAway = async (enabled: boolean) => {
    if (
      enabled &&
      (!Number.isInteger(awayDraft.duration) || awayDraft.duration < 1 || awayDraft.duration > 1440)
    ) {
      setFeedback("외출 시간을 1~1440분 사이로 입력해 주세요.");
      return;
    }
    const startsAt = enabled && awayDraft.start ? new Date(awayDraft.start) : new Date();
    if (enabled && awayDraft.start && startsAt.getTime() <= Date.now()) {
      setFeedback("예약 시작 시간은 현재보다 이후로 선택해 주세요.");
      return;
    }
    try {
      await awayMutation.mutateAsync(
        enabled
          ? {
              enabled,
              ...(awayDraft.start ? { startsAt: startsAt.toISOString() } : {}),
              endsAt: new Date(
                startsAt.getTime() + awayDraft.duration * 60_000,
              ).toISOString(),
            }
          : { enabled },
      );
      setFeedback(enabled ? "외출 설정을 저장했습니다." : "외출 모드를 해제했습니다.");
      if (enabled) setTab("home");
    } catch (cause) {
      setFeedback(getApiErrorMessage(cause));
    }
  };

  const answer = async (value: "yes" | "no") => {
    if (!notificationId) return;
    try {
      await responseMutation.mutateAsync({ notificationId, answer: value });
      setFeedback(value === "yes" ? "도움 요청이 접수됐어요." : "괜찮다는 응답이 접수됐어요.");
      clearNotification();
    } catch (cause) {
      setFeedback(getApiErrorMessage(cause));
    }
  };

  const logout = () => {
    pushSubscription.unsubscribe().catch(() => undefined);
    clearSession();
    navigate("/");
  };

  return (
    <main
      className={`user-interface min-h-screen pb-28 ${comparison ? "selection:bg-brand-200" : ""}`}
    >
      <UserHeader onLogout={logout} />
      <div className="mx-auto grid max-w-xl gap-5 px-5">
        <div>
          <h1 className="text-3xl leading-snug font-bold">
            {tab === "away"
              ? "외출 시간 정하기"
              : data
                ? `${data.name}님, 안녕하세요.`
                : "안녕하세요."}
          </h1>
          {comparison && (
            <p className="mt-2 text-sm font-semibold text-brand-700">기존 디자인 비교 화면</p>
          )}
        </div>
        {dashboard.isError && (
          <div className="rounded-xl bg-red-50 p-4 text-red-700" role="alert">
            <p>{getApiErrorMessage(dashboard.error)}</p>
            <button className="mt-2 font-bold underline" onClick={() => void dashboard.refetch()}>
              다시 불러오기
            </button>
          </div>
        )}
        {notificationId && (
          <NotificationPrompt disabled={busy || !data} onAnswer={(value) => void answer(value)} />
        )}
        <PushNotificationCard
          status={pushSubscription.status}
          error={pushSubscription.error}
          onEnable={() => void pushSubscription.enable()}
          onRetry={() => void pushSubscription.retry()}
        />
        {feedback && (
          <div
            className="flex items-center gap-3 rounded-xl border border-brand-200 bg-brand-50 p-4"
            role="status"
          >
            <p className="flex-1 text-brand-900">{feedback}</p>
            <button className="user-quiet-action" onClick={() => setFeedback("")}>
              확인하기
            </button>
          </div>
        )}
        {tab === "home" ? (
          <UserDashboard
            data={data}
            busy={busy}
            onUpdateAway={(enabled) => void updateAway(enabled)}
            onOpenAwaySettings={() => setTab("away")}
          />
        ) : (
          <AwaySettings
            draft={awayDraft}
            mode={data?.awayMode}
            busy={busy}
            dataAvailable={Boolean(data)}
            onDraftChange={setAwayDraft}
            onUpdateAway={(enabled) => void updateAway(enabled)}
          />
        )}
      </div>
      <UserNavigation tab={tab} onChange={setTab} />
    </main>
  );
}
