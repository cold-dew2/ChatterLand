"use client";

import { useState } from "react";
import { authApi } from "@/features/auth/api/authApi";
import { validatePassword } from "@/features/auth/utils/validation";
import { errorMessage, saveAuthTokens } from "@/shared/api/client";
import Button from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";

type FieldErrors = { current?: string; next?: string; confirm?: string };

/**
 * 로그인 상태에서 비밀번호를 바꾼다(학생 마이페이지·선생님 화면 공통).
 * 성공하면 서버가 다른 기기의 로그인을 모두 끊고 이 기기에 새 토큰을 주므로, 받은 토큰으로 바꿔 저장해 로그인을 유지한다.
 */
export default function ChangePasswordForm({ onChanged }: { onChanged?: () => void }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState("");
  const [done, setDone] = useState(false);
  const [saving, setSaving] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (saving) return;
    const nextErrors: FieldErrors = {
      current: current ? undefined : "현재 비밀번호를 입력해 주세요.",
      next: validatePassword(next) ?? (next === current ? "현재 비밀번호와 다른 새 비밀번호를 입력해 주세요." : undefined),
      confirm: confirm && confirm === next ? undefined : "새 비밀번호가 일치하지 않아요.",
    };
    setErrors(nextErrors);
    setServerError("");
    setDone(false);
    if (Object.values(nextErrors).some(Boolean)) return;
    setSaving(true);
    try {
      const tokens = await authApi.changePassword({ currentPassword: current, newPassword: next, newPasswordConfirm: confirm });
      saveAuthTokens(tokens.accessToken, tokens.refreshToken);
      setCurrent(""); setNext(""); setConfirm("");
      setDone(true);
      onChanged?.();
    } catch (error) {
      setServerError(errorMessage(error, "비밀번호를 변경하지 못했어요. 잠시 뒤 다시 시도해 주세요."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="space-y-3" onSubmit={(event) => void submit(event)} noValidate>
      <Input label="현재 비밀번호" type="password" autoComplete="current-password" required value={current} error={errors.current}
        onChange={(event) => { setCurrent(event.target.value); setErrors((value) => ({ ...value, current: undefined })); }} />
      <Input label="새 비밀번호" type="password" autoComplete="new-password" required value={next} error={errors.next} hint="8~72자"
        onChange={(event) => { setNext(event.target.value); setErrors((value) => ({ ...value, next: undefined })); }} />
      <Input label="새 비밀번호 확인" type="password" autoComplete="new-password" required value={confirm} error={errors.confirm}
        onChange={(event) => { setConfirm(event.target.value); setErrors((value) => ({ ...value, confirm: undefined })); }} />
      {serverError && <Notice tone="error">{serverError}</Notice>}
      {done && <Notice tone="success">비밀번호를 변경했어요. 다른 기기에서는 다시 로그인해야 해요.</Notice>}
      <Button type="submit" fullWidth loading={saving} loadingLabel="변경 중…">비밀번호 변경</Button>
    </form>
  );
}
