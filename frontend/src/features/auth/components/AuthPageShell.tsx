import type { ReactNode } from "react";
import BackButton from "@/shared/components/backButton/BackButton";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";

type AuthPageShellProps = {
  title: string;
  description?: string;
  children: ReactNode;
  /** 제목 위에 보여 줄 안내(예: 로그인 만료) */
  notice?: ReactNode;
  /** 화면 맨 아래(예: 회원가입 링크) */
  footer?: ReactNode;
  backLabel?: string;
} & ({ backHref: string; onBack?: never } | { onBack: () => void; backHref?: never });

/** 로그인·회원가입·계정 찾기 화면의 공통 틀(뒤로 가기 + 큰 제목 + 본문). 크림색 점무늬 바탕의 모바일 폭 */
export default function AuthPageShell({ title, description, notice, footer, backLabel, children, ...back }: AuthPageShellProps) {
  return (
    <div className="mx-auto flex min-h-screen w-full max-w-md flex-col surface-student px-6 pt-8 pb-8 shadow-[0_0_0_1px_var(--line-soft)]">
      {back.backHref !== undefined
        ? <BackButton href={back.backHref} label={backLabel ?? "이전 화면으로"} />
        : <BackButton onClick={back.onBack} label={backLabel ?? "처음 화면으로"} icon="arrow" />}
      <main className="mt-6 flex flex-1 flex-col">
        <PageTitle title={title} description={description} size="lg" className="mb-6" />
        {notice}
        {children}
        {footer && <div className="mt-auto pt-8">{footer}</div>}
      </main>
    </div>
  );
}
