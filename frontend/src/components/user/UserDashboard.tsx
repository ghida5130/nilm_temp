import homeImage from "../../assets/user/home.svg";
import phoneImage from "../../assets/user/phone.svg";
import walkImage from "../../assets/user/walk.svg";
import type { MyDashboard } from "../../types/monitoring";
import { telephoneHref } from "../../utils/format";
import managerIcon from "../../assets/user/manager.svg";
import alertIcon from "../../assets/user/alert.svg";

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
  const hasAwayMode = Boolean(mode?.enabled);

  return (
    <>
      {hasAwayMode ? (
        <button
          className="flex min-h-16 w-full items-center justify-center gap-2.5 rounded-2xl border border-brand-600 bg-brand-500 px-4 py-3.5 text-center text-2xl leading-6 font-semibold text-white shadow-[0_4px_0_#bd4d0d,0_7px_14px_rgb(243_121_41_/_20%)] transition-[transform,box-shadow,background-color] duration-150 hover:not-disabled:bg-[#e86b1d] active:not-disabled:translate-y-[3px] active:not-disabled:shadow-[0_1px_0_#bd4d0d,0_3px_6px_rgb(74_54_35_/_8%)] disabled:cursor-not-allowed disabled:border-stone-200 disabled:bg-stone-200 disabled:text-stone-600 disabled:shadow-none"
          disabled={busy}
          onClick={() => onUpdateAway(false)}
        >
          {/* <img className="size-8" src={homeImage} alt="" aria-hidden="true" /> */}
          {busy ? "처리 중…" : "집에 돌아왔어요"}
        </button>
      ) : (
        <button
          className="text-[32px] font-semibold flex min-h-16 w-full items-center justify-center gap-2 rounded-full border border-brand-600 bg-[linear-gradient(180deg,#f99552_0%,#f37929_58%,#e8661b_100%)] px-7 py-3.5 text-center text-white shadow-[inset_0_1px_0_rgb(255_255_255/32%),0_4px_0_#bd4d0d,0_9px_18px_rgb(120_63_27/18%)] transition-[transform,box-shadow,filter] duration-150 hover:not-disabled:brightness-[1.02] active:not-disabled:translate-y-[3px] active:not-disabled:brightness-[.97] active:not-disabled:shadow-[inset_0_1px_0_rgb(255_255_255_/_18%),0_1px_0_#bd4d0d,0_4px_8px_rgb(120_63_27_/_12%)] disabled:cursor-not-allowed disabled:border-[#c75a17] disabled:text-white disabled:brightness-[.9] disabled:shadow-none"
          disabled={!data || busy}
          onClick={onOpenAwaySettings}
        >
          <div className="size-9">
            <img src={walkImage} alt="" aria-hidden="true" />
          </div>
          <span>외출하기</span>
        </button>
      )}
      <section
        className="min-w-0 rounded-3xl border border-stone-200 bg-white p-6 shadow-[0_2px_10px_rgb(15_23_42_/_3%)] max-[359px]:p-5"
        aria-labelledby="contact-title"
      >
        {data?.manager ? (
          <>
            <div className="flex gap-4 items-center">
              <div className="size-12">
                <img src={managerIcon} />
              </div>
              <div>
                <p className="text-lg">담당자</p>
                <p className="text-2xl font-bold">{data.manager.name}</p>
              </div>
            </div>
            {data.manager.phone ? (
              <>
                {/* <p className="my-2 text-lg text-stone-600">{data.manager.phone}</p> */}
                <a
                  className="flex min-h-16 w-full text-[24px] font-semibold items-center justify-center mt-4 gap-2.5 rounded-full border border-stone-300 bg-[linear-gradient(180deg,#ffffff_0%,#fafaf9_58%,#f1f0ee_100%)] px-7 py-3.5 text-center text-stone-800 no-underline shadow-[inset_0_1px_0_rgb(255_255_255_/_90%),0_4px_0_#d6d3d1,0_9px_18px_rgb(15_23_42_/_10%)] transition-[transform,box-shadow,filter] duration-150 hover:brightness-[.99] active:translate-y-[3px] active:shadow-[inset_0_1px_0_rgb(255_255_255_/_70%),0_1px_0_#d6d3d1,0_4px_8px_rgb(15_23_42_/_8%)]"
                  href={telephoneHref(data.manager.phone)}
                >
                  <img className="size-7" src={phoneImage} alt="" aria-hidden="true" />
                  담당자에게 전화하기
                </a>
              </>
            ) : (
              // <p className="mt-4 text-stone-600">등록된 전화번호가 없습니다.</p>
              <></>
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
