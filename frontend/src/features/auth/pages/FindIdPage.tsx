"use client";

import { useState } from "react";
import Link from "next/link";
import { BookOpen, GraduationCap } from "lucide-react";
import { authApi } from "@/features/auth/api/authApi";
import AuthPageShell from "@/features/auth/components/AuthPageShell";
import { validateName } from "@/features/auth/utils/validation";
import { useCenters } from "@/features/center/hooks/useCenters";
import { ApiError, errorMessage } from "@/shared/api/client";
import { paths } from "@/routes/path/paths";
import Button, { buttonClassName } from "@/shared/components/button/Button";
import Card from "@/shared/components/card/Card";
import Notice from "@/shared/components/feedback/Notice";
import Input from "@/shared/components/input/Input";
import Select from "@/shared/components/select/Select";
import Tabs from "@/shared/components/tabs/Tabs";

type Role = "STUDENT" | "TEACHER";

export default function FindIdPage() {
  const centers = useCenters();
  const [role, setRole] = useState<Role>("STUDENT");
  const [name, setName] = useState("");
  const [center, setCenter] = useState("");
  const [errors, setErrors] = useState<{ name?: string; center?: string }>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notFound, setNotFound] = useState(false);
  const [result, setResult] = useState<string[] | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (loading) return;
    const next = { name: validateName(name), center: center ? undefined : "센터를 선택해 주세요." };
    setErrors(next);
    if (next.name || next.center) return;
    setLoading(true); setError(""); setNotFound(false); setResult(null);
    try {
      const response = await authApi.findId({ name: name.trim(), centerId: Number(center), role });
      setResult(response.maskedEmails);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 404) setNotFound(true);
      else setError(errorMessage(cause, "아이디를 찾지 못했어요. 잠시 뒤 다시 시도해 주세요."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthPageShell title="아이디 찾기" description="가입할 때 입력한 이름과 센터로 로그인 이메일을 찾아요." backHref={paths.login}>
      {result ? (
        <div className="space-y-4">
          <Card tone="raised" padding="lg" className="space-y-2">
            <p className="text-[13px] font-semibold text-[var(--meadow-700)]">가입된 이메일</p>
            <ul className="space-y-1">{result.map((email) => <li key={email} className="break-all text-lg font-bold text-[var(--ink-900)]">{email}</li>)}</ul>
            <p className="text-xs text-[var(--ink-500)]">개인정보 보호를 위해 일부만 보여드려요.</p>
          </Card>
          <Link href={paths.login} className={buttonClassName({ size: "lg", fullWidth: true })}>로그인하러 가기</Link>
          <Link href={paths.resetPassword} className={buttonClassName({ variant: "line", size: "lg", fullWidth: true })}>비밀번호 재설정</Link>
        </div>
      ) : (
        <form onSubmit={(event) => void submit(event)} className="space-y-4" noValidate>
          <Tabs ariaLabel="계정 역할" variant="outline" value={role} onChange={setRole}
            items={[{ value: "STUDENT", label: <><BookOpen size={14} aria-hidden="true" /> 학생</> }, { value: "TEACHER", label: <><GraduationCap size={14} aria-hidden="true" /> 선생님</> }]} />
          <Input label="이름" required value={name} maxLength={80} error={errors.name} autoComplete="name"
            onChange={(event) => { setName(event.target.value); setErrors((current) => ({ ...current, name: undefined })); }} />
          <Select label="언어재활센터" required value={center} options={centers.options} error={errors.center}
            placeholder={centers.state === "loading" ? "센터 목록을 불러오는 중…" : "센터를 선택하세요"} disabled={centers.state !== "ready"}
            onChange={(event) => { setCenter(event.target.value); setErrors((current) => ({ ...current, center: undefined })); }} />
          {centers.state === "error" && <div className="flex items-center gap-2"><Notice tone="error" className="flex-1">{centers.error}</Notice><Button size="sm" variant="line" onClick={centers.retry}>다시 시도</Button></div>}
          {notFound && <Notice tone="warning">입력한 정보와 일치하는 계정을 찾지 못했어요. 이름과 센터, 역할을 확인해 주세요.</Notice>}
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" size="lg" fullWidth loading={loading} loadingLabel="찾는 중…">아이디 찾기</Button>
        </form>
      )}
    </AuthPageShell>
  );
}
