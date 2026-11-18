"use client";

import { useState } from "react";
import Link from "next/link";
import { BookOpen, CheckCircle2, GraduationCap, HeartHandshake } from "lucide-react";
import { authApi, type UserRole } from "@/features/auth/api/authApi";
import AuthPageShell from "@/features/auth/components/AuthPageShell";
import { studentAgeOptions } from "@/features/auth/utils/ageOptions";
import { validateEmail, validateName, validatePassword } from "@/features/auth/utils/validation";
import { useCenters } from "@/features/center/hooks/useCenters";
import { errorMessage } from "@/shared/api/client";
import { paths } from "@/routes/path/paths";
import Button, { buttonClassName } from "@/shared/components/button/Button";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import Select from "@/shared/components/select/Select";
import Tabs from "@/shared/components/tabs/Tabs";

type Step = "identity" | "password" | "done";
type IdentityErrors = { email?: string; name?: string; center?: string; age?: string };

/**
 * 가입 정보로 본인 확인 → 새 비밀번호 설정(메일 없이, 임시 방식).
 * 서버가 이메일·이름·센터·역할(학생은 나이)이 모두 맞을 때만 재설정 토큰을 준다. 어느 항목이 틀렸는지는 알려 주지 않는다.
 */
export default function ResetPasswordPage() {
  const centers = useCenters();
  const [step, setStep] = useState<Step>("identity");
  const [role, setRole] = useState<UserRole>("STUDENT");
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [center, setCenter] = useState("");
  const [age, setAge] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [identityErrors, setIdentityErrors] = useState<IdentityErrors>({});
  const [passwordErrors, setPasswordErrors] = useState<{ password?: string; confirm?: string }>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const run = async (action: () => Promise<void>) => {
    if (loading) return;
    setLoading(true); setError("");
    try { await action(); } catch (cause) { setError(errorMessage(cause, "요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.")); } finally { setLoading(false); }
  };

  const clearIdentityError = (key: keyof IdentityErrors) => setIdentityErrors((current) => ({ ...current, [key]: undefined }));

  const verifyIdentity = (event: React.FormEvent) => {
    event.preventDefault();
    const next: IdentityErrors = {
      email: validateEmail(email),
      name: validateName(name),
      center: center ? undefined : "센터를 선택해 주세요.",
      age: role === "STUDENT" && !age ? "나이를 선택해 주세요." : undefined,
    };
    setIdentityErrors(next);
    if (Object.values(next).some(Boolean)) return;
    void run(async () => {
      const response = await authApi.verifyResetIdentity({
        email: email.trim(), name: name.trim(), centerId: Number(center), role, ...(role === "STUDENT" ? { age: Number(age) } : {}),
      });
      setResetToken(response.resetToken);
      setStep("password");
    });
  };

  const changePassword = (event: React.FormEvent) => {
    event.preventDefault();
    const next = { password: validatePassword(password), confirm: confirm && confirm === password ? undefined : "새 비밀번호가 일치하지 않아요." };
    setPasswordErrors(next);
    if (next.password || next.confirm) return;
    void run(async () => {
      await authApi.confirmPasswordReset({ resetToken, newPassword: password, newPasswordConfirm: confirm });
      setResetToken(""); setPassword(""); setConfirm("");
      setStep("done");
    });
  };

  return (
    <AuthPageShell title="비밀번호 재설정" description={step === "identity" ? "가입할 때 입력한 정보로 본인을 확인해요." : undefined} backHref={paths.login}>
      <ol className="mb-6 flex gap-1.5" aria-label="진행 단계">
        {(["identity", "password"] as const).map((item, index) => {
          const reached = ["identity", "password", "done"].indexOf(step) >= index;
          return <li key={item} aria-current={step === item ? "step" : undefined} className={`h-2 flex-1 rounded-full ${reached ? "bg-[var(--meadow-400)]" : "bg-[var(--ink-150)]"}`} />;
        })}
      </ol>

      {step === "identity" && (
        <form onSubmit={verifyIdentity} className="space-y-4" noValidate>
          <Tabs ariaLabel="계정 역할" variant="outline" value={role} onChange={(next) => { setRole(next); setError(""); clearIdentityError("age"); }}
            items={[{ value: "STUDENT", label: <><BookOpen size={14} aria-hidden="true" /> 학생</> }, { value: "TEACHER", label: <><GraduationCap size={14} aria-hidden="true" /> 선생님</> },
              { value: "GUARDIAN", label: <><HeartHandshake size={14} aria-hidden="true" /> 보호자</> }]} />
          <Input label="가입한 이메일" type="email" autoComplete="email" required value={email} error={identityErrors.email}
            onChange={(event) => { setEmail(event.target.value); clearIdentityError("email"); }} placeholder="이메일을 입력하세요" />
          <Input label="이름" required value={name} maxLength={80} autoComplete="name" error={identityErrors.name}
            onChange={(event) => { setName(event.target.value); clearIdentityError("name"); }} />
          <Select label="언어재활센터" required value={center} options={centers.options} error={identityErrors.center}
            placeholder={centers.state === "loading" ? "센터 목록을 불러오는 중…" : "센터를 선택하세요"} disabled={centers.state !== "ready"}
            onChange={(event) => { setCenter(event.target.value); clearIdentityError("center"); }} />
          {centers.state === "error" && <div className="flex items-center gap-2"><Notice tone="error" className="flex-1">{centers.error}</Notice><Button size="sm" variant="line" onClick={centers.retry}>다시 시도</Button></div>}
          {role === "STUDENT" && (
            <Select label="나이" required value={age} options={studentAgeOptions} placeholder="나이 선택" error={identityErrors.age}
              onChange={(event) => { setAge(event.target.value); clearIdentityError("age"); }} />
          )}
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="확인 중…">본인 확인</Button>
          <p className="text-center text-sm text-[var(--ink-600)]">이메일이 기억나지 않나요? <Link href={paths.findId} className="font-bold text-[var(--brand-primary)] hover:underline">아이디 찾기</Link></p>
        </form>
      )}

      {step === "password" && (
        <form onSubmit={changePassword} className="space-y-4" noValidate>
          <Notice tone="info">본인 확인이 끝났어요. 15분 안에 새 비밀번호를 정해 주세요.</Notice>
          <Input label="새 비밀번호" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password}
            hint="8자 이상 72자 이하로 입력해 주세요." error={passwordErrors.password} onChange={(event) => { setPassword(event.target.value); setPasswordErrors({}); }} />
          <Input label="새 비밀번호 확인" type="password" autoComplete="new-password" required value={confirm} error={passwordErrors.confirm}
            onChange={(event) => { setConfirm(event.target.value); setPasswordErrors({}); }} />
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
