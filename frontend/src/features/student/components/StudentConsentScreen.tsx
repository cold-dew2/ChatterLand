"use client";

import ConsentManager from "@/features/consent/components/ConsentManager";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

/** 동의 관리(별도 화면). 저장된 동의 상태를 그대로 보여 주고, 동의·철회는 기존 동의 API로만 바꾼다. */
export default function StudentConsentScreen({ onBack }: { onBack: () => void }) {
  return (
    <div className="pb-8">
      <PageHeader title="동의 관리" subtitle="개인정보·음성·AI 기능 동의를 확인하고 바꿔요" onBack={onBack} backLabel="마이페이지로 돌아가기" />
      <div className="px-5 pt-2">
        <ConsentManager hideTitle />
      </div>
    </div>
  );
}
