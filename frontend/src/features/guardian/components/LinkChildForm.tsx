"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { guardianApi, type GuardianChild } from "@/features/guardian/api/guardianApi";
import { GUARDIAN_LINK_LIMITS, GUARDIAN_LINK_SCOPE } from "@/features/guardian/utils/linkPolicy";
import { errorMessage } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";

/**
 * 자녀 연결: 자녀 계정(마이페이지 › 보호자 연결)에서 만든 1회용 코드를 입력한다.
 * 학생 ID나 이름으로는 연결할 수 없다(서버가 코드로 자녀를 찾는다).
 */
export default function LinkChildForm({ onLinked }: { onLinked: (child: GuardianChild) => void }) {
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (saving) return;
    if (code.replace(/[\s-]/g, "").length !== 8) { setError("연결 코드 8자리를 입력해 주세요."); return; }
    setSaving(true); setError("");
    try {
      const child = await guardianApi.link(code);
      setCode("");
      onLinked(child);
    } catch (cause) {
      setError(errorMessage(cause, "자녀를 연결하지 못했어요."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={(event) => void submit(event)} className="space-y-3" noValidate>
      <ol className="list-decimal space-y-1 pl-5 text-sm leading-relaxed text-[var(--ink-700)]">
        <li>자녀 계정으로 로그인해 <b>마이페이지 › 보호자 연결</b>에서 연결 코드를 만들어요.</li>
        <li>10분 안에 아래에 코드를 입력하면 연결돼요. 코드는 한 번만 쓸 수 있어요.</li>
      </ol>
      <div className="flex items-start gap-2">
        <Input label="연결 코드" placeholder="예: ABCD-2345" value={code} maxLength={11} autoComplete="off" fieldClassName="min-w-0 flex-1"
          error={error || undefined} onChange={(event) => { setCode(event.target.value.toUpperCase()); setError(""); }} />
        <Button type="submit" className="mt-[26px] min-h-[54px] shrink-0" loading={saving} loadingLabel="연결 중…">자녀 연결</Button>
      </div>
      <Notice tone="info">{GUARDIAN_LINK_SCOPE} {GUARDIAN_LINK_LIMITS}</Notice>
    </form>
  );
}
