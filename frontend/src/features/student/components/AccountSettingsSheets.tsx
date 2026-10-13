"use client";

import { useEffect, useState } from "react";
import { accountApi, type NotificationSettings } from "@/features/account/api/accountApi";
import { errorMessage } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Badge from "@/shared/components/badge/Badge";
import Notice from "@/shared/components/feedback/Notice";
import Modal from "@/shared/components/modal/Modal";

/** 아직 기능이 없어 보내지 않는 알림. 켜고 끄는 스위치를 두지 않고 준비 중으로만 알린다. */
const UPCOMING_NOTIFICATIONS = [
  { label: "선생님 메시지 알림", description: "선생님 메시지 기능이 생기면 고를 수 있어요." },
  { label: "학습 피드백 알림", description: "피드백 도착 알림은 아직 보내지 않아요." },
  { label: "휴대폰 푸시 알림", description: "앱을 닫았을 때 오는 알림은 아직 없어요." },
];

const NOTIFICATION_ITEMS: { key: keyof NotificationSettings; label: string; description: string }[] = [
  { key: "homeworkAssigned", label: "새 숙제 등록", description: "선생님이 새 숙제를 내면 알려 줘요." },
  { key: "homeworkChanged", label: "숙제 내용 변경", description: "선생님이 숙제 내용을 바꾸면 알려 줘요." },
  { key: "homeworkDueSoon", label: "숙제 마감 임박", description: "오늘·내일 마감인 숙제를 알려 줘요." },
  { key: "homeworkOverdue", label: "숙제 마감 지남", description: "마감이 지난 숙제를 알려 줘요." },
];

/** 알림 설정: 서버에 저장하고, 홈의 종 아이콘(앱 안 알림)에 반영한다. 실제로 생기는 숙제 알림만 고를 수 있다. */
export function NotificationSettingsSheet({ onClose, onSaved }: { onClose: () => void; onSaved: (settings: NotificationSettings) => void }) {
  const [settings, setSettings] = useState<NotificationSettings | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    accountApi.notificationSettings()
      .then((value) => { if (active) { setSettings(value); setState("ready"); } })
      .catch((cause: unknown) => { if (active) { setError(errorMessage(cause, "알림 설정을 불러오지 못했어요.")); setState("error"); } });
    return () => { active = false; };
  }, [retryKey]);

  const save = async () => {
    if (!settings || saving) return;
    setSaving(true); setMessage(null);
    try {
      const saved = await accountApi.saveNotificationSettings(settings);
      setSettings(saved); onSaved(saved);
      setMessage({ tone: "success", text: "알림 설정을 저장했어요." });
    } catch (cause) {
      setMessage({ tone: "error", text: errorMessage(cause, "알림 설정을 저장하지 못했어요.") });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="알림 설정" description="홈 화면의 종 아이콘에서 받을 숙제 알림을 골라요." onClose={onClose} closeDisabled={saving}
      footer={<Button fullWidth loading={saving} loadingLabel="저장 중…" disabled={state !== "ready"} onClick={() => void save()}>저장</Button>}>
      {state === "loading" && <LoadingState label="알림 설정을 불러오고 있어요…" />}
      {state === "error" && <ErrorState message={error} onRetry={() => { setState("loading"); setRetryKey((value) => value + 1); }} />}
      {state === "ready" && settings && (
        <fieldset className="space-y-2">
          <legend className="mb-1 text-[13px] font-bold text-[var(--ink-900)]">숙제 알림 · 앱 안(홈 화면 종 아이콘)</legend>
          {NOTIFICATION_ITEMS.map((item) => (
            <label key={item.key} className="flex min-h-14 cursor-pointer items-center gap-3 rounded-[var(--radius-lg)] border border-[var(--line-soft)] bg-white px-3.5 py-2.5">
              <span className="min-w-0 flex-1">
                <span className="block text-[15px] font-semibold text-[var(--ink-900)]">{item.label}</span>
                <span className="block text-xs text-[var(--ink-500)]">{item.description}</span>
              </span>
              <input type="checkbox" role="switch" checked={settings[item.key]} aria-checked={settings[item.key]}
                onChange={(event) => setSettings({ ...settings, [item.key]: event.target.checked })}
                className="h-6 w-6 shrink-0 cursor-pointer accent-[var(--brand-primary)]" />
            </label>
          ))}
        </fieldset>
      )}
      {state === "ready" && (
        <section aria-labelledby="upcoming-notifications-title" className="space-y-2">
          <h3 id="upcoming-notifications-title" className="text-[13px] font-bold text-[var(--ink-900)]">준비 중인 알림</h3>
          <ul className="space-y-2">
            {UPCOMING_NOTIFICATIONS.map((item) => (
              <li key={item.label} className="flex min-h-14 items-center gap-3 rounded-[var(--radius-lg)] border border-dashed border-[var(--line-soft)] px-3.5 py-2.5 text-[var(--ink-500)]">
                <span className="min-w-0 flex-1">
                  <span className="block text-[15px] font-semibold">{item.label}</span>
                  <span className="block text-xs">{item.description}</span>
                </span>
                <Badge tone="neutral">준비 중</Badge>
              </li>
            ))}
          </ul>
        </section>
      )}
      {message && <Notice tone={message.tone}>{message.text}</Notice>}
    </Modal>
  );
}

/** 언어 설정: 지금은 한국어만 지원한다(다국어 구조가 없어 다른 언어는 준비 중으로 표시). */
export function LanguageSettingsSheet({ onClose }: { onClose: () => void }) {
  return (
    <Modal title="언어 설정" onClose={onClose} footer={<Button fullWidth onClick={onClose}>확인</Button>}>
      <fieldset className="space-y-2">
        <legend className="sr-only">화면 언어</legend>
        <label className="flex min-h-14 items-center gap-3 rounded-[var(--radius-lg)] border-[1.5px] border-[var(--meadow-700)] bg-[var(--meadow-50)] px-3.5">
          <input type="radio" name="language" value="ko" checked readOnly className="h-5 w-5 accent-[var(--brand-primary)]" />
          <span className="flex-1 text-[15px] font-semibold text-[var(--ink-900)]">한국어</span>
          <span className="text-xs font-semibold text-[var(--meadow-800)]">사용 중</span>
        </label>
        <label className="flex min-h-14 items-center gap-3 rounded-[var(--radius-lg)] border border-[var(--line-soft)] bg-white px-3.5 text-[var(--ink-500)]">
          <input type="radio" name="language" value="en" disabled className="h-5 w-5" />
          <span className="flex-1 text-[15px] font-semibold">English</span>
          <span className="text-xs font-semibold">준비 중</span>
        </label>
      </fieldset>
    </Modal>
  );
}
