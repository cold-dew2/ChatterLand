"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Eye, EyeOff, GraduationCap, BookOpen } from "lucide-react";
import { errorMessage, saveAuthTokens } from "@/shared/api/client";
import { authApi } from "@/features/auth/api/authApi";
import AuthPageShell from "@/features/auth/components/AuthPageShell";
import { paths } from "@/routes/path/paths";
import Button, { buttonClassName } from "@/shared/components/button/Button";
import Input from "@/shared/components/input/Input";
import Notice from "@/shared/components/feedback/Notice";
import Mascot from "@/shared/components/mascot/Mascot";
import Tabs from "@/shared/components/tabs/Tabs";
import { safeReturnPath } from "@/features/auth/utils/returnPath";
import { validateEmail } from "@/features/auth/utils/validation";

type Role = "teacher" | "student";
type Step = "splash" | "login";
type FieldErrors = { email?: string; password?: string };

const roleTabs = [
  { value: "student" as Role, label: <><BookOpen size={14} aria-hidden="true" /> 학생</> },
  { value: "teacher" as Role, label: <><GraduationCap size={14} aria-hidden="true" /> 선생님</> },
];

/** 로그인 후 이동할 곳: 검증된 복귀 경로가 있으면 그곳, 아니면 역할별 홈 */
function destinationFor(userRole: string, returnTo?: string) {
  const role = userRole === "TEACHER" ? "TEACHER" : "STUDENT";
  return safeReturnPath(returnTo, role) ?? (role === "TEACHER" ? paths.teacher.home : paths.student.home);
}

export default function LoginPage({ initialStep = "splash", sessionExpired = false, returnTo }: { initialStep?: Step; sessionExpired?: boolean; returnTo?: string }) {
  const router = useRouter();
  const [step, setStep] = useState<Step>(initialStep);
  const [role, setRole] = useState<Role>("student");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  // 이미 로그인한 사용자가 로그인 화면에 오면 확인 후 원래 화면(또는 역할 홈)으로 보낸다. 토큰이 없으면 아무 요청도 하지 않는다.
  useEffect(() => {
    if (step !== "login" || !window.sessionStorage.getItem("chatterland.accessToken")) return;
    let active = true;
    authApi.me().then((user) => { if (active) router.replace(destinationFor(user.role, returnTo)); }).catch(() => undefined);
    return () => { active = false; };
  }, [step, returnTo, router]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;
    setError("");
    const nextErrors: FieldErrors = { email: validateEmail(email), password: password ? undefined : "비밀번호를 입력해 주세요." };
    setFieldErrors(nextErrors);
    if (nextErrors.email || nextErrors.password) return;
    setLoading(true);
    try {
      const result = await authApi.login({ email: email.trim(), password });
      if (result.user.role !== role.toUpperCase()) {
        // 선택한 역할과 다른 계정이면 발급된 토큰을 저장하지 않고 즉시 폐기한다.
        await authApi.revoke(result.refreshToken).catch(() => undefined);
        setError(`선택한 ${role === "student" ? "학생" : "선생님"} 계정이 아닙니다. 역할을 확인해 주세요.`);
        return;
      }
      saveAuthTokens(result.accessToken, result.refreshToken);
      router.push(destinationFor(result.user.role, returnTo));
    } catch (cause) {
      setError(errorMessage(cause, "로그인에 실패했어요. 다시 시도해 주세요."));
    } finally {
      setLoading(false);
    }
  };

  // ── Splash ──────────────────────────────────────────────────────────────────

  if (step === "splash") {
    return (
      <div className="relative mx-auto flex min-h-screen w-full max-w-md flex-col overflow-hidden surface-student shadow-[0_0_0_1px_var(--line-soft)]">
        {/* 초원 일러스트: 위쪽은 바탕색으로 자연스럽게 사라진다 */}
        <div className="absolute inset-x-0 bottom-0 h-[62%] [mask-image:linear-gradient(transparent_0%,#000_38%)]" aria-hidden="true">
          <Mascot variant="scene" eager className="object-[50%_70%]" />
        </div>

        <div className="relative flex flex-1 flex-col px-7 pt-16 pb-10">
          <p className="flex items-center gap-2 text-sm font-bold tracking-wide text-[var(--ink-900)]">
            <span className="h-[26px] w-[26px] rounded-[8px_8px_8px_2px] bg-[var(--brand-primary)]" aria-hidden="true" />ChatterLand
          </p>
          <h1 className="mt-10 font-display text-5xl leading-[1.08] tracking-[-0.04em] text-[var(--ink-900)]">채터랜드</h1>
          <p className="mt-3 text-[17px] leading-relaxed text-[var(--ink-600)]">
            우리 아이의 말하기 성장을<br />함께 응원해요!
          </p>

          {/* 그림을 가리지 않도록 버튼은 아래에 모은다 */}
          <div className="mt-auto space-y-2.5 pt-64">
            <Button size="lg" fullWidth onClick={() => setStep("login")} className="text-[17px]">
              로그인
            </Button>
            <Link href={paths.signup} className={buttonClassName({ variant: "line", size: "lg", fullWidth: true, className: "border-transparent bg-white/95 text-[17px] text-[var(--ink-900)]" })}>
              회원가입
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // ── Login form ──────────────────────────────────────────────────────────────

  return (
    <AuthPageShell title="로그인" description="역할을 선택한 뒤 이메일로 로그인해 주세요" onBack={() => setStep("splash")}
      notice={sessionExpired && <Notice tone="warning" className="mb-4">로그인 시간이 만료되었어요. 다시 로그인해 주세요.</Notice>}
      footer={(
        <p className="text-center text-sm text-[var(--ink-600)]">
          계정이 없으신가요?{" "}
          <Link href={paths.signup} className="font-bold text-[var(--brand-primary)] hover:underline">회원가입</Link>
        </p>
      )}>
      <Tabs ariaLabel="로그인 역할 선택" variant="outline" items={roleTabs} value={role}
        onChange={(value) => { setRole(value); setError(""); }} className="mb-6" />

      <form onSubmit={handleLogin} className="space-y-4" noValidate>
        <Input
          id="login-email" label="이메일" type="email" autoComplete="email" required value={email}
          error={fieldErrors.email}
          onChange={(e) => { setEmail(e.target.value); setError(""); setFieldErrors((current) => ({ ...current, email: undefined })); }}
          placeholder="이메일을 입력하세요"
        />
        <Input
          id="login-password" label="비밀번호" type={showPw ? "text" : "password"} autoComplete="current-password" required value={password}
          error={fieldErrors.password}
          onChange={(e) => { setPassword(e.target.value); setError(""); setFieldErrors((current) => ({ ...current, password: undefined })); }}
          placeholder="비밀번호를 입력하세요"
          endAdornment={<button type="button" onClick={() => setShowPw(!showPw)}
            aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 표시"}
            className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-[var(--ink-100)] hover:text-[var(--ink-800)]">
            {showPw ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>}
        />

        {error && <Notice tone="error">{error}</Notice>}
        <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="로그인 중…" className="mt-1">로그인</Button>
      </form>

      <p className="mt-4 flex items-center justify-center gap-3 text-sm text-[var(--ink-600)]">
        <Link href={paths.findId} className="rounded-md px-1 py-1 transition-colors hover:text-[var(--ink-900)] hover:underline">아이디 찾기</Link>
        <span aria-hidden="true" className="h-3 w-px bg-[var(--line-control)]" />
        <Link href={paths.resetPassword} className="rounded-md px-1 py-1 transition-colors hover:text-[var(--ink-900)] hover:underline">비밀번호 찾기</Link>
      </p>

      {/* 소셜 로그인 */}
      <div className="mt-7">
        <div className="mb-3 flex items-center gap-2.5">
          <div className="h-px flex-1 bg-[var(--line-soft)]" />
          <span className="text-xs text-[var(--ink-500)]">소셜 로그인은 준비 중이에요.</span>
          <div className="h-px flex-1 bg-[var(--line-soft)]" />
        </div>
        <div className="flex justify-center gap-3">
          {[
            // 각 서비스의 로고 색은 브랜드 규정 색이라 그대로 쓴다.
            { bg: "bg-white border border-[var(--line-soft)]", content: <svg viewBox="0 0 24 24" className="w-5 h-5"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg> },
            { bg: "bg-[var(--ink-950)]", content: <svg viewBox="0 0 24 24" className="w-5 h-5 fill-white"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg> },
            { bg: "bg-[#FEE500]", content: <span className="text-lg font-black" style={{ color: "#3A1D1D" }}>K</span> },
          ].map((s, i) => (
            <button key={i} type="button" disabled aria-label="소셜 로그인은 준비 중입니다"
              className={`flex h-[52px] w-[52px] cursor-not-allowed items-center justify-center rounded-[var(--radius-xl)] opacity-55 ${s.bg}`}>
              {s.content}
            </button>
          ))}
        </div>
      </div>
    </AuthPageShell>
  );
}
