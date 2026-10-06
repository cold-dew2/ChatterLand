"use client";

import { useState } from "react";
import Link from "next/link";
import { CheckCircle2 } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
import AuthPageShell from "@/features/auth/components/AuthPageShell";
import { validateEmail, validatePassword } from "@/features/auth/utils/validation";
import { errorMessage } from "@/shared/api/client";
import { paths } from "@/routes/path/paths";
import Button, { buttonClassName } from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";

type Step = "email" | "code" | "password" | "done";

/** 이메일 인증 코드 → 새 비밀번호 설정. 서버가 본인 확인(코드 검증)을 마친 경우에만 비밀번호를 바꿀 수 있다. */
export default function ResetPasswordPage() {
  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fieldError, setFieldError] = useState<{ email?: string; code?: string; password?: string; confirm?: string }>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");

  const run = async (action: () => Promise<void>) => {
    if (loading) return;
    setLoading(true); setError("");
    try { await action(); } catch (cause) { setError(errorMessage(cause, "요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.")); } finally { setLoading(false); }
  };

  const requestCode = (event?: React.FormEvent) => {
    event?.preventDefault();
    const emailError = validateEmail(email);
    setFieldError({ email: emailError });
    if (emailError) return;
    void run(async () => {
      const response = await authApi.requestPasswordReset(email.trim());
      setInfo(response.message);
      setStep("code");
    });
  };

  const verifyCode = (event: React.FormEvent) => {
    event.preventDefault();
    if (!/^\d{6}$/.test(code)) { setFieldError({ code: "메일로 받은 6자리 숫자를 입력해 주세요." }); return; }
    setFieldError({});
    void run(async () => {
      const response = await authApi.verifyResetCode(email.trim(), code);
      setResetToken(response.resetToken);
      setInfo("");
      setStep("password");
    });
  };

  const changePassword = (event: React.FormEvent) => {
    event.preventDefault();
    const next = { password: validatePassword(password), confirm: confirm && confirm === password ? undefined : "새 비밀번호가 일치하지 않아요." };
    setFieldError(next);
    if (next.password || next.confirm) return;
    void run(async () => {
      await authApi.confirmPasswordReset({ resetToken, newPassword: password, newPasswordConfirm: confirm });
      setResetToken(""); setPassword(""); setConfirm("");
      setStep("done");
    });
  };

  return (
    <AuthPageShell title="비밀번호 재설정" description={step === "done" ? undefined : "가입한 이메일로 인증 코드를 보내 본인을 확인해요."} backHref={paths.login}>
      <ol className="mb-6 flex gap-1.5" aria-label="진행 단계">
        {(["email", "code", "password"] as const).map((item, index) => {
          const reached = ["email", "code", "password", "done"].indexOf(step) >= index;
          return <li key={item} aria-current={step === item ? "step" : undefined} className={`h-2 flex-1 rounded-full ${reached ? "bg-[var(--meadow-400)]" : "bg-[var(--ink-150)]"}`} />;
        })}
      </ol>

      {step === "email" && (
        <form onSubmit={requestCode} className="space-y-4" noValidate>
          <Input label="가입한 이메일" type="email" autoComplete="email" required value={email} error={fieldError.email}
            onChange={(event) => { setEmail(event.target.value); setFieldError({}); }} placeholder="이메일을 입력하세요" />
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="보내는 중…">인증 코드 받기</Button>
          <p className="text-center text-sm text-[var(--ink-600)]">이메일이 기억나지 않나요? <Link href={paths.findId} className="font-bold text-[var(--brand-primary)] hover:underline">아이디 찾기</Link></p>
        </form>
      )}

      {step === "code" && (
        <form onSubmit={verifyCode} className="space-y-4" noValidate>
          {info && <Notice tone="info">{info}</Notice>}
          <Input label="인증 코드 (6자리)" inputMode="numeric" autoComplete="one-time-code" maxLength={6} required value={code} error={fieldError.code}
            hint="코드는 10분 동안 한 번만 쓸 수 있고, 5번 틀리면 다시 받아야 해요." onChange={(event) => { setCode(event.target.value.replace(/\D/g, "")); setFieldError({}); }} />
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="확인 중…">인증하기</Button>
          <div className="flex justify-between text-sm">
            <button type="button" onClick={() => { setStep("email"); setCode(""); setError(""); }} className="min-h-10 rounded-md px-1 text-[var(--ink-600)] hover:underline">이메일 다시 입력</button>
            <button type="button" disabled={loading} onClick={() => requestCode()} className="min-h-10 rounded-md px-1 font-semibold text-[var(--brand-primary)] hover:underline disabled:opacity-50">코드 다시 받기</button>
          </div>
        </form>
      )}

      {step === "password" && (
        <form onSubmit={changePassword} className="space-y-4" noValidate>
          <Input label="새 비밀번호" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password}
            hint="8자 이상 72자 이하로 입력해 주세요." error={fieldError.password} onChange={(event) => { setPassword(event.target.value); setFieldError({}); }} />
          <Input label="새 비밀번호 확인" type="password" autoComplete="new-password" required value={confirm} error={fieldError.confirm}
            onChange={(event) => { setConfirm(event.target.value); setFieldError({}); }} />
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="변경 중…">비밀번호 변경</Button>
        </form>
      )}

      {step === "done" && (
        <div className="space-y-5 pt-4 text-center">
          <span className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--meadow-100)]" aria-hidden="true"><CheckCircle2 size={40} className="text-[var(--meadow-700)]" /></span>
          <p className="font-display text-2xl text-[var(--ink-900)]">비밀번호를 변경했어요</p>
          <p className="text-sm leading-relaxed text-[var(--ink-600)]">보안을 위해 다른 기기의 로그인은 모두 해제됐어요. 새 비밀번호로 로그인해 주세요.</p>
          <Link href={paths.login} className={buttonClassName({ size: "lg", fullWidth: true })}>로그인하러 가기</Link>
        </div>
      )}
    </AuthPageShell>
  );
}
