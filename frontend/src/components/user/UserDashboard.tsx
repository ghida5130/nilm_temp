import type { MyDashboard } from "../../types/monitoring";
import { formatShortDateTime, telephoneHref } from "../../utils/format";
import Icon from "../common/Icon";

type UserDashboardProps = {
  data?: MyDashboard;
  busy: boolean;
  onUpdateAway: (enabled: boolean) => void;
  onOpenAwaySettings: () => void;
};

export default function UserDashboard({
  data,
  busy,
  onUpdateAway,
  onOpenAwaySettings,
}: UserDashboardProps) {
  const mode = data?.awayMode;
  const hasAwayMode = Boolean(mode?.enabled || mode?.scheduled);

  return (
    <>
      <section className="user-card" aria-labelledby="outing-title">
        <h2 className="text-2xl font-bold" id="outing-title">
          외출 알림
        </h2>
        <div className="mt-5 rounded-xl bg-brand-50 p-4">
          <strong className="text-xl">
            {!data
              ? "정보를 불러오고 있어요"
              : mode?.enabled
                ? "외출 중입니다"
                : mode?.scheduled
                  ? "외출을 예약했어요"
                  : "등록된 외출이 없어요"}
          </strong>
          {mode?.enabled && (
            <p className="mt-1 text-lg text-stone-600">
              {formatShortDateTime(mode.until)}까지
            </p>
          )}
          {mode?.scheduled && (
            <p className="mt-1 text-lg text-stone-600">
              {formatShortDateTime(mode.startedAt)}부터
            </p>
          )}
        </div>
        <div className="mt-6 grid gap-3">
          {hasAwayMode && (
            <button
              className="user-action user-action-primary"
              disabled={busy}
              onClick={() => onUpdateAway(false)}
            >
              <Icon name={mode?.enabled ? "home" : "close"} />
              {busy ? "처리 중…" : mode?.enabled ? "집에 돌아왔어요" : "외출 예약 취소"}
            </button>
          )}
          <button
            className={`user-action ${hasAwayMode ? "user-action-secondary" : "user-action-primary"}`}
            disabled={!data || busy}
            onClick={onOpenAwaySettings}
          >
            <Icon name="walk" />
            {hasAwayMode ? "외출 시간 바꾸기" : "외출 시간 정하기"}
            <Icon name="arrow" />
          </button>
        </div>
      </section>
      <section className="user-card" aria-labelledby="contact-title">
        <h2 className="text-2xl font-bold" id="contact-title">
          나의 복지담당자
        </h2>
        {data?.manager ? (
          <>
            <p className="mt-5 text-2xl font-bold">
              {data.manager.name}
              <span className="text-lg font-normal"> 담당자</span>
            </p>
            {data.manager.phone ? (
              <>
                <p className="my-2 text-lg text-stone-600">{data.manager.phone}</p>
                <a
                  className="user-action user-action-secondary"
                  href={telephoneHref(data.manager.phone)}
                >
                  <Icon name="phone" />
                  담당자에게 전화하기
                </a>
              </>
            ) : (
              <p className="mt-4 text-stone-600">등록된 전화번호가 없습니다.</p>
            )}
          </>
        ) : (
          <p className="mt-4 text-stone-600">
            {data ? "아직 배정된 담당자가 없습니다." : "담당자 정보를 불러오고 있어요."}
          </p>
        )}
      </section>
    </>
  );
}
