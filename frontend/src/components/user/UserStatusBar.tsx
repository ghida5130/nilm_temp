import { useEffect, useRef } from "react";
import { Lottie } from "lottie-react";
import checkAnimation from "../../assets/lottie/check.json";
import type { MyDashboard } from "../../types/monitoring";
import { formatShortDateTime } from "../../utils/format";
import Icon from "../common/Icon";

type UserStatusBarProps = {
  data?: MyDashboard;
  notice: UserStatusNotice;
  noticeVisible: boolean;
  notificationPending: boolean;
  notificationDisabled: boolean;
  onAnswerNotification: (answer: "yes" | "no") => void;
};

export type UserStatusNotice = {
  message: string;
  tone: "success" | "error";
} | null;

export default function UserStatusBar({
  data,
  notice,
  noticeVisible,
  notificationPending,
  notificationDisabled,
  onAnswerNotification,
}: UserStatusBarProps) {
  const isLoading = !data;
  const isAway = Boolean(data?.awayMode.enabled);
  const statusText = isLoading ? "상태 확인 중" : isAway ? "외출 중" : "재실 중";
  const statusKey = isLoading ? "loading" : isAway ? "away" : "home";
  const usesAnimatedCheck =
    notice?.tone === "success" &&
    (notice.message === "외출 설정 완료" ||
      notice.message === "귀가 설정 완료" ||
      notice.message === "도움 요청이 접수됐어요." ||
      notice.message === "괜찮다는 응답이 접수됐어요.");
  const showNotice = noticeVisible && !notificationPending;
  const statusRow = useRef<HTMLDivElement>(null);
  const previousStatus = useRef(statusKey);

  useEffect(() => {
    if (previousStatus.current === statusKey) return;
    previousStatus.current = statusKey;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const animation = statusRow.current?.animate(
      [
        { opacity: 0, transform: "translateY(6px)" },
        { opacity: 1, transform: "translateY(0)" },
      ],
      { duration: 280, easing: "cubic-bezier(0.22, 1, 0.36, 1)" },
    );

    return () => animation?.cancel();
  }, [statusKey]);

  return (
    <div
      className={`fixed inset-x-0 bottom-0 z-10 mx-auto max-w-xl border-t px-6 pt-3.5 pb-[calc(.875rem+env(safe-area-inset-bottom))] shadow-[0_-8px_24px_rgb(41_37_36_/_7%)] transition-colors duration-300 ease-out motion-reduce:transition-none ${isAway ? "border-brand-200 bg-brand-50 text-brand-900" : "border-stone-200 bg-white text-stone-800"}`}
      aria-live="polite"
    >
      <div
        className={`grid transition-[grid-template-rows,margin,opacity] duration-300 ease-out motion-reduce:transition-none ${notificationPending ? "mb-3 grid-rows-[1fr] opacity-100" : "mb-0 grid-rows-[0fr] opacity-0"}`}
        aria-hidden={!notificationPending}
      >
        <div className="min-h-0 overflow-hidden">
          <section className="pb-3" aria-labelledby="response-title">
            <h2 className="text-xl font-bold" id="response-title">
              지금 상태를 알려주세요
            </h2>
            <p className="mt-1 text-base text-stone-600">선택한 응답을 담당자에게 전달해요.</p>
            <div
              className={`grid transition-[grid-template-rows,margin,opacity] duration-200 ease-out motion-reduce:transition-none ${noticeVisible && notice?.tone === "error" ? "mt-2 grid-rows-[1fr] opacity-100" : "mt-0 grid-rows-[0fr] opacity-0"}`}
              aria-hidden={!(noticeVisible && notice?.tone === "error")}
            >
              <p
                className="min-h-0 overflow-hidden text-base font-semibold text-red-700"
                role={noticeVisible && notice?.tone === "error" ? "alert" : undefined}
              >
                {notice?.message}
              </p>
            </div>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <button
                className="flex min-h-14 items-center justify-center rounded-2xl border border-[#9b312c] bg-[#b33b35] px-3 py-3 text-lg leading-snug font-semibold text-white transition-colors hover:not-disabled:bg-[#96312c] disabled:cursor-not-allowed disabled:border-stone-200 disabled:bg-stone-200 disabled:text-stone-600"
                type="button"
                disabled={notificationDisabled || !notificationPending}
                onClick={() => onAnswerNotification("yes")}
              >
                도움이 필요해요
              </button>
              <button
                className="flex min-h-14 items-center justify-center rounded-2xl border border-stone-300 bg-white px-3 py-3 text-lg leading-snug font-semibold text-stone-800 transition-colors hover:not-disabled:bg-stone-50 disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-600"
                type="button"
                disabled={notificationDisabled || !notificationPending}
                onClick={() => onAnswerNotification("no")}
              >
                괜찮아요
              </button>
            </div>
          </section>
        </div>
      </div>
      <div
        className={`grid transition-[grid-template-rows,margin,opacity] duration-300 ease-out motion-reduce:transition-none ${showNotice ? "mb-3 grid-rows-[1fr] opacity-100" : "mb-0 grid-rows-[0fr] opacity-0"}`}
        aria-hidden={!showNotice}
      >
        <div className="min-h-0 overflow-hidden">
          <div
            className={`flex items-center gap-2.5 pb-3 text-lg font-semibold ${notice?.tone === "error" ? "text-red-700" : "text-stone-800"}`}
            role={showNotice ? (notice?.tone === "error" ? "alert" : "status") : undefined}
          >
            {notice?.tone === "error" ? (
              <Icon name="bell" />
            ) : usesAnimatedCheck ? (
              <Lottie
                key={`${notice?.message}-${showNotice}`}
                className="-my-1 size-8 shrink-0"
                src={checkAnimation}
                autoplay
                loop={false}
                aria-hidden="true"
              />
            ) : (
              <Icon name="check" />
            )}
            <span>{notice?.message}</span>
          </div>
        </div>
      </div>
      <div ref={statusRow} className="flex flex-col items-center gap-2">
        <span className="flex items-center justify-center text-stone-500">
          {isLoading && <Icon className="size-7" name="clock" />}
        </span>
        <div className="min-w-0">
          <p className="text-2xl mb-2 text-center leading-tight font-bold tracking-[-0.02em]">
            {statusText}
          </p>
          <div
            className={`grid transition-[grid-template-rows,margin,opacity] duration-300 ease-out motion-reduce:transition-none ${isAway ? "mt-1 grid-rows-[1fr] opacity-100" : "mt-0 grid-rows-[0fr] opacity-0"}`}
          >
            <p className="min-h-0 overflow-hidden text-base leading-snug font-medium text-brand-700">
              {formatShortDateTime(data?.awayMode.until)}까지
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
