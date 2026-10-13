"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { authApi } from "@/features/auth/api/authApi";
import { clearAuth } from "@/shared/api/client";

/** 로그아웃(학생·선생님·보호자 공통): 서버 세션을 끊고, 실패해도 이 기기의 로그인 정보는 항상 지운 뒤 처음 화면으로 */
export function useLogout() {
  const router = useRouter();
  const [loggingOut, setLoggingOut] = useState(false);
  const logout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try { await authApi.logout(); } catch { /* always clear the local session */ } finally { clearAuth(); router.push("/"); }
  };
  return { logout, loggingOut };
}
