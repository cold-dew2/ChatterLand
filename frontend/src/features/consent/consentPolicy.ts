/**
 * 개인정보·아동 음성 처리 안내문. 백엔드 ConsentPolicy.CURRENT_VERSION과 버전을 맞춘다.
 * 실제 데이터 흐름(기본 설정: SPEECH_ENGINE=local)을 기준으로 작성했다.
 * 외부 음성 분석 제공자(SPEECH_ENGINE=external)를 쓰도록 바꾸면 음성 외부 전송 내용을 반영해 문구와 버전을 갱신해야 한다.
 * 법정대리인 동의 기준(만 14세 미만)은 운영 기준이며 확정된 법률 자문이 아니다.
 */
export const CONSENT_POLICY_VERSION = '2026-10-01'
export const GUARDIAN_REQUIRED_UNDER_AGE = 14
export const AUDIO_RETENTION_MONTHS = 6

export type ConsentType = 'PRIVACY' | 'GUARDIAN' | 'VOICE' | 'AI_CHAT' | 'AI_FEEDBACK'

export type ConsentSection = { heading: string; items: string[] }

export const consentDocuments: Record<ConsentType, { title: string; summary: string; sections: ConsentSection[] }> = {
  PRIVACY: {
    title: '개인정보 수집·이용 동의',
    summary: '회원 관리와 학습 지원을 위해 필요한 최소한의 정보를 수집해요.',
    sections: [
      { heading: '수집 항목', items: ['이름, 이메일(로그인 아이디), 비밀번호(암호화하여 저장), 소속 언어재활센터', '학생: 나이, 숙제·연습 기록, 문장 연습 결과', '선생님이 등록한 정보: 보호자 연락처, 치료 영역, 메모'] },
      { heading: '이용 목적', items: ['회원 식별과 로그인, 비밀번호 재설정 메일 발송', '말하기 연습·숙제 제공과 학습 기록 관리', '담당 선생님의 학습 관리와 학습 리포트 작성'] },
      { heading: '접근 권한', items: ['본인, 배정된 담당 선생님, 서비스 운영 관리자만 접근할 수 있어요.', '다른 선생님이나 다른 학생은 정보를 볼 수 없어요.'] },
      { heading: '보관 기간', items: ['회원 탈퇴 시까지 보관하고, 관계 법령에 따라 보관이 필요한 경우 그 기간 동안 보관해요.'] },
      { heading: '철회와 문의', items: ['필수 동의 철회(회원 탈퇴)와 개인정보 문의는 소속 언어재활센터에 요청해 주세요.', '필수 동의를 하지 않으면 가입할 수 없어요.'] },
    ],
  },
  GUARDIAN: {
    title: '법정대리인(보호자) 동의',
    summary: `만 ${GUARDIAN_REQUIRED_UNDER_AGE}세 미만 아동의 개인정보는 법정대리인의 동의가 필요해요.`,
    sections: [
      { heading: '확인 내용', items: ['보호자 이름과 아동과의 관계를 입력하고, 보호자가 직접 안내 내용을 확인했음을 표시해요.', '현재는 보호자 휴대폰·전자서명 등 별도 본인 확인 절차가 없어요. 센터가 대면으로 확인해야 할 수 있어요.'] },
    ],
  },
  VOICE: {
    title: '아동 음성 수집·이용 동의 (선택)',
    summary: '말하기 연습을 하려면 아이의 목소리를 녹음해야 해요.',
    sections: [
      { heading: '수집 항목', items: ['말하기 연습과 AI 대화 중 녹음한 음성', '음성을 글자로 바꾼 인식 결과와 목표 문장 비교 결과'] },
      { heading: '처리 방식', items: ['녹음은 채터랜드 서버에서 공개 음성 인식 모델(Whisper)로 글자로 바꿔요.', '음성 파일은 외부 음성·AI 서비스로 보내지 않아요.', '인식 결과는 발음 정확도나 진단 결과가 아니에요.'] },
      { heading: '보관 기간', items: ['일반 문장 연습 녹음과 AI 대화 녹음은 글자로 바꾼 직후 삭제해요.', `언어재활 학습자의 녹음은 선생님 검토를 위해 최대 ${AUDIO_RETENTION_MONTHS}개월 보관한 뒤 자동으로 삭제해요.`, '인식 결과와 선생님 검토 기록은 학습 기록으로 계정과 함께 보관해요.'] },
      { heading: '철회', items: ['마이페이지의 동의 관리에서 언제든 철회할 수 있어요.', '철회하면 보관 중인 녹음을 즉시 삭제하고 음성 기능을 사용할 수 없어요.', '동의하지 않아도 숙제 확인 등 다른 기능은 이용할 수 있어요.'] },
    ],
  },
  AI_CHAT: {
    title: 'AI 대화 외부 전송 동의 (선택)',
    summary: 'AI 대화는 외부 AI 서비스를 이용해요.',
    sections: [
      { heading: '전송 항목', items: ['대화 주제와 대화 내용(글자). 음성으로 말한 경우 서버에서 글자로 바꾼 내용만 보내요.', '이름, 이메일, 음성 파일은 보내지 않아요.'] },
      { heading: '받는 곳', items: ['센터가 설정한 외부 AI 대화 서비스(제공자와 보관 정책은 센터에 문의해 주세요).'] },
      { heading: '철회', items: ['마이페이지의 동의 관리에서 철회하면 AI 대화를 사용할 수 없어요.'] },
    ],
  },
  AI_FEEDBACK: {
    title: 'AI 학습 피드백 외부 전송 동의 (선택)',
    summary: '연습 결과 아래의 \'AI 설명\'은 외부 AI 서비스를 이용해 만들어요. AI 대화 동의와는 별개예요.',
    sections: [
      { heading: '이용 목적', items: ['말하기 연습 결과를 아이가 이해하기 쉬운 설명과 연습 방법으로 풀어 쓰기 위해서예요.', 'AI 설명은 발음 점수나 진단이 아니에요. 발음 정확도는 평가하지 않아요.'] },
      { heading: '전송 항목', items: ['목표 문장, 컴퓨터가 알아들은 문장(글자), 자동으로 찾은 오류 후보 또는 선생님이 확정한 결과', '설명의 근거로 쓰는 한국어 발음 교육 자료 문단'] },
      { heading: '보내지 않는 것', items: ['이름, 이메일, 계정 정보, 음성 파일, 선생님 메모, 점수·수치'] },
      { heading: '받는 곳', items: ['센터가 설정한 외부 AI 서비스(제공자와 보관 정책은 센터에 문의해 주세요).'] },
      { heading: '보관과 열람', items: ['만들어진 설명은 학습 기록과 함께 채터랜드에 보관하고, 본인과 담당 선생님이 볼 수 있어요.'] },
      { heading: '철회', items: ['마이페이지의 동의 관리에서 언제든 철회할 수 있어요. 철회하면 새 AI 설명을 만들지 않아요(외부 전송 없음).', '이미 만들어진 설명은 학습 기록으로 남아요. 삭제를 원하면 소속 센터에 요청해 주세요.'] },
    ],
  },
}

export const consentLabels: Record<ConsentType, string> = {
  PRIVACY: '[필수] 개인정보 수집·이용',
  GUARDIAN: '[필수] 법정대리인(보호자) 동의',
  VOICE: '[선택] 아동 음성 수집·이용',
  AI_CHAT: '[선택] AI 대화 외부 전송',
  AI_FEEDBACK: '[선택] AI 학습 피드백 외부 전송',
}

export function guardianRequired(role: 'student' | 'teacher', age: number | null) {
  return role === 'student' && (age === null || age < GUARDIAN_REQUIRED_UNDER_AGE)
}
