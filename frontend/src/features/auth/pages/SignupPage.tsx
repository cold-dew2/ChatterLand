"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Eye, EyeOff, GraduationCap, BookOpen } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
import AuthPageShell from "@/features/auth/components/AuthPageShell";
import { validateEmail, validateName, validatePassword } from "@/features/auth/utils/validation";
import { errorMessage } from "@/shared/api/client";
import { paths } from "@/routes/path/paths";
import Button from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import Input from "@/shared/components/input/Input";
import Select from "@/shared/components/select/Select";
import Notice from "@/shared/components/feedback/Notice";
import Mascot from "@/shared/components/mascot/Mascot";
import Tabs from "@/shared/components/tabs/Tabs";
import { useCenters } from "@/features/center/hooks/useCenters";
import SignupConsentFields, { emptySignupConsents, validateSignupConsents, type SignupConsentErrors, type SignupConsentValues } from "@/features/consent/components/SignupConsentFields";
import { CONSENT_POLICY_VERSION, guardianRequired } from "@/features/consent/consentPolicy";

type Role = "student" | "teacher";
type SignupForm = { name: string; email: string; password: string; age: string; center: string };
type FieldErrors = Partial<Record<keyof SignupForm, string>>;

const ages = Array.from({ length: 13 }, (_, i) => ({ value: String(i + 4), label: `${i + 4}세` }));

const roleTabs = [
  { value: "student" as Role, label: <><BookOpen size={14} aria-hidden="true" /> 학생</> },
  { value: "teacher" as Role, label: <><GraduationCap size={14} aria-hidden="true" /> 선생님</> },
];

const emptyForm: SignupForm = { name: "", email: "", password: "", age: "", center: "" };

export default function SignupPage() {
  const router = useRouter();
  const [role, setRole] = useState<Role>("student");
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);
  const [consents, setConsents] = useState<SignupConsentValues>(emptySignupConsents);
  const [consentErrors, setConsentErrors] = useState<SignupConsentErrors>({});
  const centers = useCenters();
  const [form, setForm] = useState<SignupForm>(emptyForm);
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  const update = (key: keyof SignupForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm((current) => ({ ...current, [key]: e.target.value }));
    setFieldErrors((current) => ({ ...current, [key]: undefined }));
    setError("");
  };

  const validate = (): FieldErrors => ({
    name: validateName(form.name),
    email: validateEmail(form.email),
    password: validatePassword(form.password),
    age: role === "student" && !form.age ? "나이를 선택해 주세요." : undefined,
    center: form.center ? undefined : "언어재활센터를 선택해 주세요.",
  });
  const needsGuardian = guardianRequired(role, form.age ? Number(form.age) : null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (loading) return;
    const nextErrors = validate();
    const nextConsentErrors = validateSignupConsents(consents, needsGuardian);
    setFieldErrors(nextErrors);
    setConsentErrors(nextConsentErrors);
    if (Object.values(nextErrors).some(Boolean) || Object.values(nextConsentErrors).some(Boolean)) return;
    setError("");
    setLoading(true);
    try {
      await authApi.signup({
        role: role === "student" ? "STUDENT" : "TEACHER",
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        centerId: Number(form.center),
        termsAgreed: consents.privacy,
        age: role === "student" ? Number(form.age) : undefined,
        consents: {
          privacy: consents.privacy, voice: role === "student" && consents.voice, aiChat: role === "student" && consents.aiChat,
          policyVersion: CONSENT_POLICY_VERSION,
          ...(needsGuardian ? { guardianConfirmed: consents.guardianConfirmed, guardianName: consents.guardianName.trim(), guardianRelation: consents.guardianRelation.trim() } : {}),
        },
      });
      setDone(true);
    } catch (cause) {
      setError(errorMessage(cause, "가입 요청을 처리하지 못했어요."));
    } finally {
      setLoading(false);
    }
  };

  // ── Done screen ──────────────────────────────────────────────────────────────

  if (done) {
    const centerName = centers.options.find((center) => center.value === form.center)?.label ?? "";
    const rows = [
      { label: "역할", value: role === "student" ? "학생" : "선생님" },
      ...(role === "student" ? [{ label: "나이", value: `${form.age}세` }] : []),
      { label: "센터", value: centerName },
      ...(role === "teacher" ? [{ label: "이메일", value: form.email }] : []),
    ];
    return (
      <div className="mx-auto flex min-h-screen w-full max-w-md flex-col surface-student px-6 pt-16 pb-8 shadow-[0_0_0_1px_var(--line-soft)]">
        <main className="flex flex-1 flex-col items-center justify-center gap-5 text-center">
          <Mascot size={128} alt="채터랜드 공룡 친구" />
          <div>
            <h2 className="font-display text-[28px] leading-tight text-[var(--ink-900)]">가입 완료!</h2>
            <p className="mt-1 text-[15px] text-[var(--ink-600)]">
              환영해요, <span className="font-bold text-[var(--meadow-700)]">{form.name || "새 회원"}</span>님!
            </p>
          </div>
          <Card tone="raised" padding="lg" className="w-full text-left">
            <p className="mb-2 text-xs font-bold text-[var(--meadow-700)]">가입 정보</p>
            <dl className="grid grid-cols-[5.5rem_1fr] gap-y-2 text-sm">
              {rows.map((row) => (
                <div key={row.label} className="contents">
                  <dt className="text-[var(--ink-500)]">{row.label}</dt>
                  <dd className="min-w-0 break-all font-semibold text-[var(--ink-900)]">{row.value}</dd>
                </div>
              ))}
            </dl>
          </Card>
        </main>
        <Button size="lg" fullWidth onClick={() => router.push(paths.login)}>로그인 화면으로 이동</Button>
      </div>
    );
  }

  // ── Main signup form ──────────────────────────────────────────────────────────

  return (
    <AuthPageShell title="회원가입" backHref={paths.root} backLabel="처음 화면으로"
      footer={(
        <p className="text-center text-sm text-[var(--ink-600)]">
          이미 계정이 있으신가요?{" "}
          <Link href={paths.login} className="font-bold text-[var(--brand-primary)] hover:underline">로그인</Link>
        </p>
      )}>
      <Tabs ariaLabel="가입 역할 선택" variant="outline" items={roleTabs} value={role}
        onChange={(value) => { setRole(value); setFieldErrors({}); setError(""); }} className="mb-6" />

      <form onSubmit={(event) => void handleSubmit(event)} className="space-y-4" noValidate>
        <Input id="signup-name" label="이름" required maxLength={80} value={form.name} onChange={update("name")}
          placeholder="이름을 입력하세요" error={fieldErrors.name} autoComplete="name" />
        <Input id="signup-email" label="이메일" type="email" autoComplete="email" required value={form.email}
          onChange={update("email")} placeholder="이메일을 입력하세요" error={fieldErrors.email} />
        <Input
          id="signup-password" label="비밀번호" type={showPw ? "text" : "password"} required minLength={8} maxLength={72}
          autoComplete="new-password" value={form.password} onChange={update("password")} placeholder="8자 이상 입력하세요"
          hint="8자 이상 72자 이하로 입력해 주세요." error={fieldErrors.password}
          endAdornment={<button type="button" onClick={() => setShowPw(!showPw)}
            aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 표시"} className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-[var(--ink-100)] hover:text-[var(--ink-800)]">
            {showPw ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>}
        />
        <div className={role === "student" ? "grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-2" : undefined}>
          {role === "student" && (
            <Select id="signup-age" label="나이" required value={form.age} onChange={update("age")}
              placeholder="나이 선택" options={ages} error={fieldErrors.age} />
          )}
          <Select id="signup-center" label={role === "student" ? "언어재활센터" : "소속 언어재활센터"} required value={form.center}
            onChange={update("center")} placeholder={centers.state === "loading" ? "센터 목록을 불러오는 중…" : "센터를 선택하세요"}
            options={centers.options} disabled={centers.state !== "ready" || centers.options.length === 0}
            error={fieldErrors.center ?? (centers.state === "ready" && centers.options.length === 0 ? "가입할 수 있는 센터가 없어요. 센터에 문의해 주세요." : undefined)} />
        </div>
        {centers.state === "error" && (
          <div className="flex items-center gap-2"><Notice tone="error" className="flex-1">{centers.error}</Notice><Button size="sm" variant="line" onClick={centers.retry}>다시 시도</Button></div>
        )}

        <SignupConsentFields role={role} needsGuardian={needsGuardian} values={consents} errors={consentErrors}
          onChange={(values) => { setConsents(values); setConsentErrors({}); }} />

        {error && <Notice tone="error">{error}</Notice>}
        <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="처리 중…" className="mt-1">회원가입</Button>
      </form>
    </AuthPageShell>
  );
}
