/** 학생 나이 선택지(회원가입·비밀번호 재설정 본인 확인 공통) */
export const studentAgeOptions = Array.from({ length: 13 }, (_, i) => ({ value: String(i + 4), label: `${i + 4}세` }));
