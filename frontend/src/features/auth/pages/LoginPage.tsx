"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Eye, EyeOff, ArrowRight, GraduationCap, BookOpen } from "lucide-react";
import { errorMessage, saveAuthTokens } from "@/shared/api/client";
import { authApi } from "@/features/auth/api/authApi";
import { paths } from "@/routes/path/paths";
import Button, { buttonClassName } from "@/shared/components/button/Button";
import Input from "@/shared/components/input/Input";
import Notice from "@/shared/components/feedback/Notice";
import Tabs from "@/shared/components/tabs/Tabs";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";
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
      <div
        className="min-h-screen flex flex-col items-center justify-center px-8 bg-white"
       
      >
        <div className="w-full max-w-xs flex flex-col items-center gap-0">

          {/* Logo wordmark */}
          <div className="text-center mb-2">
            <p className="text-lg font-bold tracking-wide" style={{ color: "var(--brand-primary)" }}>ChatterLand</p>
            <h1 className="text-4xl font-black text-gray-900 leading-tight">채터랜드</h1>
          </div>

          {/* Tagline */}
          <p className="text-sm text-gray-500 text-center mb-6">
            우리 아이의 말하기 성장을<br />함께 응원해요!
          </p>

          {/* Dino + chick illustration */}
          <div className="relative flex items-end justify-center mb-10" style={{ height: 180 }}>
            {/* Main dino */}
            <div className="relative">
              <svg viewBox="0 0 160 160" width="160" height="160" xmlns="http://www.w3.org/2000/svg">
                {/* Body */}
                <ellipse cx="80" cy="110" rx="45" ry="42" fill="#4ade80" />
                {/* Belly */}
                <ellipse cx="80" cy="118" rx="28" ry="26" fill="#bbf7d0" />
                {/* Neck */}
                <ellipse cx="72" cy="74" rx="22" ry="28" fill="#4ade80" />
                {/* Head */}
                <ellipse cx="68" cy="50" rx="28" ry="24" fill="#4ade80" />
                {/* Snout */}
                <ellipse cx="86" cy="56" rx="14" ry="10" fill="#86efac" />
                {/* Nostril */}
                <circle cx="91" cy="52" r="2.5" fill="#16a34a" />
                {/* Eye white */}
                <circle cx="62" cy="42" r="9" fill="white" />
                {/* Eye */}
                <circle cx="64" cy="43" r="5.5" fill="#1e3a5f" />
                {/* Eye shine */}
                <circle cx="66" cy="41" r="2" fill="white" />
                {/* Smile */}
                <path d="M78 62 Q85 68 92 62" stroke="#16a34a" strokeWidth="2" fill="none" strokeLinecap="round" />
                {/* Left arm */}
                <ellipse cx="42" cy="108" rx="10" ry="16" fill="#4ade80" transform="rotate(-20 42 108)" />
                <ellipse cx="37" cy="122" rx="6" ry="5" fill="#86efac" transform="rotate(-20 37 122)" />
                {/* Right arm (waving) */}
                <ellipse cx="118" cy="90" rx="10" ry="16" fill="#4ade80" transform="rotate(40 118 90)" />
                <ellipse cx="128" cy="78" rx="6" ry="5" fill="#86efac" transform="rotate(40 128 78)" />
                {/* Left leg */}
                <ellipse cx="62" cy="148" rx="12" ry="10" fill="#4ade80" />
                <ellipse cx="58" cy="156" rx="14" ry="6" fill="#86efac" />
                {/* Right leg */}
                <ellipse cx="96" cy="148" rx="12" ry="10" fill="#4ade80" />
                <ellipse cx="100" cy="156" rx="14" ry="6" fill="#86efac" />
                {/* Tail */}
                <path d="M120 130 Q148 118 152 100 Q148 90 138 96 Q130 110 120 122Z" fill="#4ade80" />
                {/* Back spikes */}
                <polygon points="58,28 52,10 64,22" fill="#16a34a" />
                <polygon points="72,24 68,6 78,18" fill="#16a34a" />
                <polygon points="86,26 84,8 93,20" fill="#16a34a" />
              </svg>
            </div>
            {/* Little chick friend */}
            <div className="absolute bottom-8 right-0">
              <svg viewBox="0 0 60 60" width="60" height="60" xmlns="http://www.w3.org/2000/svg">
                {/* Body */}
                <ellipse cx="30" cy="38" rx="18" ry="16" fill="#fbbf24" />
                {/* Head */}
                <circle cx="30" cy="22" r="14" fill="#fbbf24" />
                {/* Wing */}
                <ellipse cx="46" cy="36" rx="8" ry="6" fill="#f59e0b" transform="rotate(30 46 36)" />
                {/* Eye */}
                <circle cx="26" cy="20" r="4" fill="#1e293b" />
                <circle cx="27" cy="19" r="1.5" fill="white" />
                {/* Beak */}
                <polygon points="33,24 40,21 33,28" fill="#f97316" />
                {/* Blush */}
                <ellipse cx="22" cy="26" r="3" fill="#fca5a5" opacity="0.6" />
                {/* Feet */}
                <line x1="24" y1="52" x2="20" y2="58" stroke="#f59e0b" strokeWidth="2.5" strokeLinecap="round" />
                <line x1="24" y1="52" x2="28" y2="58" stroke="#f59e0b" strokeWidth="2.5" strokeLinecap="round" />
                <line x1="36" y1="52" x2="32" y2="58" stroke="#f59e0b" strokeWidth="2.5" strokeLinecap="round" />
                <line x1="36" y1="52" x2="40" y2="58" stroke="#f59e0b" strokeWidth="2.5" strokeLinecap="round" />
              </svg>
            </div>
            {/* Stars decoration */}
            <div className="absolute top-2 right-4 text-yellow-400 text-xl">✦</div>
            <div className="absolute top-8 left-2 text-blue-500 text-sm">✦</div>
          </div>

          {/* Buttons */}
          <div className="w-full space-y-3">
            <Button size="lg" fullWidth onClick={() => setStep("login")} className="shadow-lg shadow-blue-200">
              로그인
            </Button>
            <Link href={paths.signup} className={buttonClassName({ variant: "outline", size: "lg", fullWidth: true })}>
              회원가입
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // ── Login form ──────────────────────────────────────────────────────────────

  return (
    <div
      className="min-h-screen bg-white flex flex-col"
     
    >
      {/* Back */}
      <div className="px-5 pt-5">
        <Button variant="ghost" size="icon" onClick={() => setStep("splash")} aria-label="처음 화면으로">
          <ArrowRight size={18} className="rotate-180" />
        </Button>
      </div>

      <div className="flex-1 flex items-center justify-center px-6 pb-8">
        <div className="w-full max-w-sm">

          <PageTitle title="로그인" size="lg" className="mb-6" />
          {sessionExpired && <Notice tone="warning" className="mb-4">로그인 시간이 만료되었어요. 다시 로그인해 주세요.</Notice>}

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
                className="text-gray-400 hover:text-gray-600">
                {showPw ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>}
            />

            {error && <Notice tone="error">{error}</Notice>}
            <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="로그인 중…">로그인</Button>
          </form>

          <p className="mt-3 flex items-center justify-center gap-3 text-sm text-gray-400">
            <Link href={paths.findId} className="transition-colors hover:text-gray-600 hover:underline">아이디 찾기</Link>
            <span aria-hidden="true" className="h-3 w-px bg-gray-200" />
            <Link href={paths.resetPassword} className="transition-colors hover:text-gray-600 hover:underline">비밀번호 찾기</Link>
          </p>

          {/* 소셜 로그인 */}
          <div className="mt-5">
            <p className="mb-3 text-center text-xs text-gray-400">소셜 로그인은 준비 중이에요.</p>
            <div className="flex items-center gap-3 mb-4">
              <div className="flex-1 h-px bg-gray-100" />
              <span className="text-xs text-gray-400">또는</span>
              <div className="flex-1 h-px bg-gray-100" />
            </div>
            <div className="flex justify-center gap-4">
              {[
                { bg: "bg-white border border-gray-200", content: <svg viewBox="0 0 24 24" className="w-5 h-5"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg> },
                { bg: "bg-black", content: <svg viewBox="0 0 24 24" className="w-5 h-5 fill-white"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg> },
                { bg: "bg-[#FEE500]", content: <span className="text-lg font-black" style={{ color: "#3A1D1D" }}>K</span> },
              ].map((s, i) => (
                <button key={i} type="button" disabled aria-label="소셜 로그인은 준비 중입니다"
                  className={`w-14 h-14 rounded-2xl flex items-center justify-center shadow-sm opacity-60 ${s.bg}`}>
                  {s.content}
                </button>
              ))}
            </div>
          </div>

          {/* 회원가입 링크 */}
          <p className="text-center text-sm text-gray-400 mt-6">
            계정이 없으신가요?{" "}
            <Link href={paths.signup} className="font-bold text-[var(--brand-primary)] hover:underline">회원가입</Link>
          </p>

        </div>
      </div>
    </div>
  );
}
