"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Eye, EyeOff, ChevronLeft, CheckCircle2, GraduationCap, BookOpen } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
import { validateEmail, validateName, validatePassword } from "@/features/auth/utils/validation";
import { errorMessage } from "@/shared/api/client";
import { paths } from "@/routes/path/paths";
import Button from "@/shared/components/button/Button";
import Input from "@/shared/components/input/Input";
import Select from "@/shared/components/select/Select";
import Notice from "@/shared/components/feedback/Notice";
import Tabs from "@/shared/components/tabs/Tabs";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";
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
    return (
      <div className="min-h-screen bg-white flex flex-col items-center justify-center px-8">
        <div className="w-full max-w-xs flex flex-col items-center gap-5 text-center">
          <div className="w-20 h-20 rounded-full bg-blue-50 flex items-center justify-center">
            <CheckCircle2 size={44} className="text-blue-700" />
          </div>
          <div>
            <h2 className="text-2xl font-bold text-gray-900 mb-1">가입 완료! 🎉</h2>
            <p className="text-sm text-gray-500">
              환영해요, <span className="font-bold text-blue-800">{form.name || "새 회원"}</span>님!
            </p>
          </div>
          <div className="w-full bg-blue-50 rounded-2xl p-4 text-left space-y-1.5">
            <p className="text-xs font-semibold text-blue-800 mb-2">가입 정보</p>
            <p className="text-sm text-gray-600">역할: <span className="font-medium">{role === "student" ? "학생" : "선생님"}</span></p>
            {role === "student" && <p className="text-sm text-gray-600">나이: <span className="font-medium">{form.age}세</span></p>}
            <p className="text-sm text-gray-600">센터: <span className="font-medium">{centerName}</span></p>
            {role === "teacher" && <p className="text-sm text-gray-600">이메일: <span className="font-medium">{form.email}</span></p>}
          </div>
          <Button size="lg" fullWidth onClick={() => router.push(paths.login)}>로그인 화면으로 이동</Button>
        </div>
      </div>
    );
  }

  // ── Main signup form ──────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <div className="px-5 pt-5 flex items-center gap-2">
        <Link href={paths.root} aria-label="처음 화면으로" className="p-2 rounded-xl hover:bg-gray-100 text-gray-400">
          <ChevronLeft size={20} />
        </Link>
      </div>

      <div className="flex-1 flex items-center justify-center px-6 pb-8">
        <div className="w-full max-w-xs">
          <PageTitle title="회원가입" size="lg" className="mb-6" />

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
                aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 표시"} className="text-gray-400 hover:text-gray-600">
                {showPw ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>}
            />
            {role === "student" && (
              <Select id="signup-age" label="나이" required value={form.age} onChange={update("age")}
                placeholder="나이를 선택하세요" options={ages} error={fieldErrors.age} />
            )}
            <Select id="signup-center" label={role === "student" ? "언어재활센터" : "소속 언어재활센터"} required value={form.center}
              onChange={update("center")} placeholder={centers.state === "loading" ? "센터 목록을 불러오는 중…" : "센터를 선택하세요"}
              options={centers.options} disabled={centers.state !== "ready" || centers.options.length === 0}
              error={fieldErrors.center ?? (centers.state === "ready" && centers.options.length === 0 ? "가입할 수 있는 센터가 없어요. 센터에 문의해 주세요." : undefined)} />
            {centers.state === "error" && (
              <div className="flex items-center gap-2"><Notice tone="error" className="flex-1">{centers.error}</Notice><Button size="sm" variant="line" onClick={centers.retry}>다시 시도</Button></div>
            )}

            <SignupConsentFields role={role} needsGuardian={needsGuardian} values={consents} errors={consentErrors}
              onChange={(values) => { setConsents(values); setConsentErrors({}); }} />

            {error && <Notice tone="error">{error}</Notice>}
            <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="처리 중…" className="mt-1">회원가입</Button>
          </form>

          <p className="text-center text-sm text-gray-400 mt-5">
            이미 계정이 있으신가요?{" "}
            <Link href={paths.login} className="font-bold text-[var(--brand-primary)] hover:underline">로그인</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
