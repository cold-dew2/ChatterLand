import Link from "next/link";
import type { ReactNode } from "react";
import { ChevronLeft } from "lucide-react";
import { PageTitle } from "@/shared/components/pageHeader/PageHeader";

/** 로그인·회원가입·계정 찾기 화면의 공통 틀(뒤로 가기 + 가운데 정렬 본문) */
export default function AuthPageShell({ title, description, backHref, children }: { title: string; description?: string; backHref: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-white">
      <div className="flex items-center gap-2 px-5 pt-5">
        <Link href={backHref} aria-label="이전 화면으로" className="rounded-xl p-2 text-gray-400 hover:bg-gray-100"><ChevronLeft size={20} /></Link>
      </div>
      <main className="flex flex-1 items-center justify-center px-6 pb-8">
        <div className="w-full max-w-sm">
          <PageTitle title={title} description={description} size="lg" className="mb-6" />
          {children}
        </div>
      </main>
    </div>
  );
}
