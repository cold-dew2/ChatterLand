"use client";

import { useEffect, useState } from "react";
import { accountApi } from "@/features/account/api/accountApi";
import { authApi } from "@/features/auth/api/authApi";
import ChangePasswordForm from "@/features/auth/components/ChangePasswordForm";
import { validateName } from "@/features/auth/utils/validation";
import { errorMessage } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import ErrorState from "@/shared/components/feedback/ErrorState";
import LoadingState from "@/shared/components/feedback/LoadingState";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import PageHeader from "@/shared/components/pageHeader/PageHeader";

type Account = { name: string; email: string; centerName: string };

/**
 * 내 정보 수정(별도 화면). 서버가 바꿀 수 있게 허용한 이름(PATCH /account/profile)과 비밀번호(기존 비밀번호 변경 API)를 각각 따로 저장한다.
 * 이메일(로그인 아이디)·나이(보호자 동의 기준)·센터는 보기만 한다. 생년월일은 저장하는 곳이 없어 받지 않는다.
 */
export default function StudentProfileEditScreen({ age, onBack, onProfileChanged }: {
  /** 학생 프로필의 나이(없으면 '-') */
  age?: number | null;
  onBack: () => void;
  onProfileChanged?: () => void;
}) {
  const [account, setAccount] = useState<Account | null>(null);
  const [loadError, setLoadError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [nameDraft, setNameDraft] = useState("");
  const [nameError, setNameError] = useState("");
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    authApi.me()
      .then((user) => {
        if (!active) return;
        setAccount({ name: user.name, email: user.email, centerName: user.centerName ?? "" });
        setNameDraft(user.name);
      })
      .catch((cause) => { if (active) setLoadError(errorMessage(cause, "내 정보를 불러오지 못했어요.")); });
    return () => { active = false; };
  }, [retryKey]);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (saving || !account) return;
    const problem = validateName(nameDraft);
    setNameError(problem ?? ""); setMessage(null);
    if (problem) return;
    setSaving(true);
    try {
      const user = await accountApi.updateProfile({ name: nameDraft.trim() });
      setAccount({ ...account, name: user.name }); setNameDraft(user.name);
      setMessage({ tone: "success", text: "이름을 바꿨어요." });
      onProfileChanged?.();
    } catch (cause) {
      setMessage({ tone: "error", text: errorMessage(cause, "이름을 바꾸지 못했어요.") });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="pb-8">
      <PageHeader title="내 정보 수정" onBack={onBack} backLabel="마이페이지로 돌아가기" />
      <div className="space-y-4 px-5 pt-2">
        {!account && !loadError && <LoadingState label="내 정보를 불러오고 있어요…" />}
        {loadError && <ErrorState message={loadError} onRetry={() => { setLoadError(""); setRetryKey((value) => value + 1); }} />}
        {account && (
          <Card as="section" padding="lg" aria-labelledby="profile-basic-title" className="space-y-4">
            <h3 id="profile-basic-title" className="text-base font-bold text-[var(--ink-900)]">기본 정보</h3>
            <form className="flex items-start gap-2" onSubmit={(event) => void save(event)} noValidate>
              <Input label="이름" value={nameDraft} maxLength={80} error={nameError || undefined} fieldClassName="min-w-0 flex-1" autoComplete="name"
                onChange={(event) => { setNameDraft(event.target.value); setNameError(""); setMessage(null); }} />
              <Button type="submit" variant="secondary" className="mt-[26px] min-h-[54px] shrink-0" loading={saving} loadingLabel="저장 중…"
                disabled={nameDraft.trim() === account.name}>이름 저장</Button>
            </form>
            {message && <Notice tone={message.tone}>{message.text}</Notice>}
            <dl className="grid grid-cols-[4.5rem_1fr] gap-y-2 rounded-[var(--radius-lg)] bg-[var(--surface-sunken)] px-3.5 py-3 text-sm">
              <dt className="text-[var(--ink-500)]">이메일</dt><dd className="min-w-0 break-all font-semibold text-[var(--ink-900)]">{account.email || "-"}</dd>
              <dt className="text-[var(--ink-500)]">나이</dt><dd className="font-semibold text-[var(--ink-900)]">{age ? `${age}세` : "-"}</dd>
              <dt className="text-[var(--ink-500)]">센터</dt><dd className="font-semibold text-[var(--ink-900)]">{account.centerName || "-"}</dd>
            </dl>
            <p className="text-xs leading-relaxed text-[var(--ink-500)]">이메일은 로그인 아이디라서, 나이는 보호자 동의 기준이라서 이 화면에서 바꿀 수 없어요. 바꾸려면 센터에 문의해 주세요.</p>
          </Card>
        )}
        {/* 이름 저장과 별개의 폼·API. 성공해도 이 기기 로그인은 유지되고 다른 기기는 끊긴다(기존 정책 그대로) */}
        <Card as="section" padding="lg" aria-labelledby="password-change-title" className="space-y-3">
          <h3 id="password-change-title" className="text-base font-bold text-[var(--ink-900)]">비밀번호 변경</h3>
          <ChangePasswordForm />
        </Card>
      </div>
    </div>
  );
}
