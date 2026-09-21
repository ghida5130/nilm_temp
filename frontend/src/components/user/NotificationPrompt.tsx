type NotificationPromptProps = {
  disabled: boolean;
  onAnswer: (answer: "yes" | "no") => void;
};

export default function NotificationPrompt({ disabled, onAnswer }: NotificationPromptProps) {
  return (
    <section className="user-card user-card-prompt" aria-labelledby="response-title">
      <h2 className="text-2xl font-bold" id="response-title">
        지금 상태를 알려주세요
      </h2>
      <p className="mt-2 text-lg text-stone-600">선택한 응답을 담당자에게 전달해요.</p>
      <div className="mt-6 grid gap-3">
        <button
          className="user-action user-action-danger"
          disabled={disabled}
          onClick={() => onAnswer("yes")}
        >
          도움이 필요해요
        </button>
        <button
          className="user-action user-action-secondary"
          disabled={disabled}
          onClick={() => onAnswer("no")}
        >
          괜찮아요
        </button>
      </div>
    </section>
  );
}
