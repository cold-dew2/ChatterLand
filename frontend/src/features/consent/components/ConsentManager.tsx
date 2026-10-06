"use client";

import { useEffect, useState } from "react";
import { consentApi, type Consent } from "@/features/consent/api/consentApi";
import ConsentDocumentModal from "@/features/consent/components/ConsentDocumentModal";
import { CONSENT_POLICY_VERSION, consentLabels, type ConsentType } from "@/features/consent/consentPolicy";
import { errorMessage } from "@/shared/api/client";
import Badge from "@/shared/components/badge/Badge";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import ConfirmDialog from "@/shared/components/feedback/ConfirmDialog";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import Modal from "@/shared/components/modal/Modal";

/** 마이페이지 동의 관리: 동의 내역 확인, 선택 동의 변경·철회 */
export default function ConsentManager() {
  const [consents, setConsents] = useState<Consent[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [detail, setDetail] = useState<ConsentType | null>(null);
  const [withdrawTarget, setWithdrawTarget] = useState<ConsentType | null>(null);
  const [agreeTarget, setAgreeTarget] = useState<ConsentType | null>(null);
  const [guardian, setGuardian] = useState({ name: "", relation: "", confirmed: false });
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    let active = true;
    consentApi.mine().then((rows) => { if (active) { setConsents(rows); setState("ready"); } })
      .catch((cause: unknown) => { if (active) { setError(errorMessage(cause, "동의 내역을 불러오지 못했어요.")); setState("error"); } });
    return () => { active = false; };
  }, [retryKey]);

  const needsGuardian = consents.some((consent) => consent.type === "GUARDIAN");

  const update = async (type: ConsentType, agreed: boolean) => {
    setPending(true); setMessage(null);
    try {
      const updated = await consentApi.update(type, {
        agreed, policyVersion: CONSENT_POLICY_VERSION,
        ...(agreed && needsGuardian ? { guardianConfirmed: guardian.confirmed, guardianName: guardian.name.trim(), guardianRelation: guardian.relation.trim() } : {}),
      });
      setConsents((current) => current.map((consent) => consent.type === type ? updated : consent));
      setMessage({ tone: "success", text: agreed ? "동의했어요." : type === "VOICE" ? "동의를 철회하고 보관 중인 녹음을 삭제했어요." : "동의를 철회했어요." });
      setWithdrawTarget(null); setAgreeTarget(null);
    } catch (cause) {
      setMessage({ tone: "error", text: errorMessage(cause, "동의 상태를 바꾸지 못했어요.") });
    } finally {
      setPending(false);
    }
  };

  return (
    <section aria-labelledby="consent-manager-title" className="space-y-3">
      <h3 id="consent-manager-title" className="text-base font-bold text-[var(--ink-900)]">동의 관리</h3>
      {detail && <ConsentDocumentModal type={detail} onClose={() => setDetail(null)} />}
      {withdrawTarget && <ConfirmDialog title="동의를 철회할까요?" confirmLabel="철회" pending={pending}
        description={withdrawTarget === "VOICE" ? "보관 중인 녹음이 즉시 삭제되고, 말하기 연습과 음성 대화를 사용할 수 없어요."
          : withdrawTarget === "AI_FEEDBACK" ? "새 AI 설명을 만들 수 없어요. 이미 만들어진 설명은 학습 기록으로 남아요." : "AI 대화를 사용할 수 없어요."}
        onCancel={() => setWithdrawTarget(null)} onConfirm={() => update(withdrawTarget, false)} />}
      {agreeTarget && (
        <Modal title={`${consentLabels[agreeTarget]} 동의`} onClose={() => setAgreeTarget(null)} closeDisabled={pending}
          footer={<Button fullWidth loading={pending} loadingLabel="저장 중…" onClick={() => void update(agreeTarget, true)}
            disabled={needsGuardian && (!guardian.confirmed || !guardian.name.trim() || !guardian.relation.trim())}>동의하기</Button>}>
          <Button size="sm" variant="secondary" onClick={() => setDetail(agreeTarget)}>안내 내용 보기</Button>
          {needsGuardian && <>
            <Notice tone="warning">만 14세 미만 학생은 보호자가 직접 동의해야 해요.</Notice>
            <div className="grid grid-cols-2 gap-2">
              <Input label="보호자 이름" size="sm" value={guardian.name} onChange={(event) => setGuardian((current) => ({ ...current, name: event.target.value }))} />
              <Input label="아동과의 관계" size="sm" value={guardian.relation} onChange={(event) => setGuardian((current) => ({ ...current, relation: event.target.value }))} placeholder="예: 부모" />
            </div>
            <label className="flex min-h-9 cursor-pointer items-start gap-3 text-sm text-[var(--ink-700)]">
              <input type="checkbox" checked={guardian.confirmed} onChange={(event) => setGuardian((current) => ({ ...current, confirmed: event.target.checked }))} className="mt-px h-5 w-5 shrink-0 accent-[var(--brand-primary)]" />
              보호자인 제가 안내를 확인하고 동의합니다.
            </label>
          </>}
          {message?.tone === "error" && <Notice tone="error">{message.text}</Notice>}
        </Modal>
      )}
      {state === "loading" && <LoadingState label="동의 내역을 불러오고 있어요…" />}
      {state === "error" && <ErrorState message={error} onRetry={() => { setState("loading"); setRetryKey((value) => value + 1); }} />}
      {message && !agreeTarget && <Notice tone={message.tone}>{message.text}</Notice>}
      {state === "ready" && (
        <Card padding="none" className="divide-y divide-[var(--ink-100)] overflow-hidden">
          {consents.map((consent) => (
            <div key={consent.type} className="flex min-h-16 flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3">
              <div className="min-w-0 flex-1 basis-40">
                <p className="text-sm font-semibold text-[var(--ink-900)]">{consentLabels[consent.type]}</p>
                <p className="text-xs text-[var(--ink-500)]">
                  {consent.agreed ? `동의 ${consent.agreedAt ?? ""}` : consent.withdrawnAt ? `철회 ${consent.withdrawnAt}` : "동의하지 않음"}
                  {consent.agreed && !consent.currentVersion ? " · 안내문이 갱신되어 다시 동의가 필요해요" : ""}
                </p>
              </div>
              <Badge tone={consent.agreed ? "success" : "neutral"}>{consent.agreed ? "동의" : "미동의"}</Badge>
              {consent.required
                ? <button type="button" onClick={() => setDetail(consent.type)} className="min-h-10 rounded-md px-1.5 text-[13px] font-semibold text-[var(--brand-primary)] hover:underline">보기</button>
                : consent.agreed && consent.currentVersion
                  ? <Button size="sm" variant="line" onClick={() => setWithdrawTarget(consent.type)}>철회</Button>
                  : <Button size="sm" onClick={() => { setMessage(null); setAgreeTarget(consent.type); }}>동의</Button>}
            </div>
          ))}
        </Card>
      )}
      <p className="text-xs leading-relaxed text-[var(--ink-500)]">필수 동의 철회(회원 탈퇴)와 개인정보 문의는 소속 언어재활센터에 요청해 주세요.</p>
    </section>
  );
}
