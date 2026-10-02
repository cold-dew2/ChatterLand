"use client";

import { useState } from "react";
import ConsentCheckbox from "@/features/consent/components/ConsentCheckbox";
import ConsentDocumentModal from "@/features/consent/components/ConsentDocumentModal";
import { consentLabels, type ConsentType } from "@/features/consent/consentPolicy";
import Card from "@/shared/components/card/Card";
import Input from "@/shared/components/input/Input";

export type SignupConsentValues = {
  privacy: boolean; voice: boolean; aiChat: boolean;
  guardianConfirmed: boolean; guardianName: string; guardianRelation: string;
};

export type SignupConsentErrors = Partial<Record<"privacy" | "guardianConfirmed" | "guardianName" | "guardianRelation", string>>;

export const emptySignupConsents: SignupConsentValues = { privacy: false, voice: false, aiChat: false, guardianConfirmed: false, guardianName: "", guardianRelation: "" };

export function validateSignupConsents(values: SignupConsentValues, needsGuardian: boolean): SignupConsentErrors {
  return {
    privacy: values.privacy ? undefined : "개인정보 수집·이용(필수)에 동의해 주세요.",
    guardianName: needsGuardian && !values.guardianName.trim() ? "보호자 이름을 입력해 주세요." : undefined,
    guardianRelation: needsGuardian && !values.guardianRelation.trim() ? "아동과의 관계를 입력해 주세요." : undefined,
    guardianConfirmed: needsGuardian && !values.guardianConfirmed ? "보호자가 안내를 확인하고 동의했음을 표시해 주세요." : undefined,
  };
}

/** 회원가입 동의 영역. 학생은 음성·AI 선택 동의와 (만 14세 미만) 법정대리인 확인을 함께 받는다. */
export default function SignupConsentFields({ role, needsGuardian, values, errors, onChange }: {
  role: "student" | "teacher"; needsGuardian: boolean; values: SignupConsentValues; errors: SignupConsentErrors;
  onChange: (values: SignupConsentValues) => void;
}) {
  const [detail, setDetail] = useState<ConsentType | null>(null);
  const student = role === "student";
  const allChecked = values.privacy && (!student || (values.voice && values.aiChat)) && (!needsGuardian || values.guardianConfirmed);
  const set = (patch: Partial<SignupConsentValues>) => onChange({ ...values, ...patch });

  return (
    <fieldset>
      <legend className="mb-2 block text-sm font-semibold text-gray-700">약관 및 개인정보 동의</legend>
      {detail && <ConsentDocumentModal type={detail} onClose={() => setDetail(null)} />}
      <Card tone="muted" padding="sm" className="space-y-1">
        <ConsentCheckbox bold label="전체 동의 (선택 항목 포함)" checked={allChecked}
          onChange={(checked) => set({ privacy: checked, voice: student && checked, aiChat: student && checked, guardianConfirmed: needsGuardian && checked })} />
        <div className="h-px bg-gray-200" />
        <ConsentCheckbox label={consentLabels.PRIVACY} checked={values.privacy} onChange={(privacy) => set({ privacy })} onDetail={() => setDetail("PRIVACY")} error={errors.privacy} />
        {student && <>
          <ConsentCheckbox label={consentLabels.VOICE} checked={values.voice} onChange={(voice) => set({ voice })} onDetail={() => setDetail("VOICE")} />
          <ConsentCheckbox label={consentLabels.AI_CHAT} checked={values.aiChat} onChange={(aiChat) => set({ aiChat })} onDetail={() => setDetail("AI_CHAT")} />
          <p className="pl-6 text-[11px] leading-relaxed text-gray-400">선택 동의를 하지 않으면 말하기 연습(음성 녹음)이나 AI 대화를 이용할 수 없어요. 마이페이지에서 나중에 바꿀 수 있어요.</p>
        </>}
      </Card>
      {needsGuardian && (
        <Card padding="sm" className="mt-3 space-y-3">
          <p className="text-xs font-semibold text-gray-700">만 14세 미만 학생은 법정대리인(보호자) 동의가 필요해요.</p>
          <div className="grid grid-cols-2 gap-2">
            <Input label="보호자 이름" size="sm" required maxLength={80} value={values.guardianName} error={errors.guardianName}
              onChange={(event) => set({ guardianName: event.target.value })} autoComplete="off" />
            <Input label="아동과의 관계" size="sm" required maxLength={20} value={values.guardianRelation} error={errors.guardianRelation}
              onChange={(event) => set({ guardianRelation: event.target.value })} placeholder="예: 부모" />
          </div>
          <ConsentCheckbox label={`${consentLabels.GUARDIAN} — 보호자인 제가 위 안내를 확인하고 동의합니다.`} checked={values.guardianConfirmed}
            onChange={(guardianConfirmed) => set({ guardianConfirmed })} onDetail={() => setDetail("GUARDIAN")} error={errors.guardianConfirmed} />
        </Card>
      )}
    </fieldset>
  );
}
