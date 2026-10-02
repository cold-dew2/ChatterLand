export const paths = {
  root: '/',
  login: '/login',
  signup: '/signup',
  findId: '/find-id',
  resetPassword: '/reset-password',
  student: {
    home: '/student',
    practice: '/student/practice',
    aiChat: '/student/ai-chat',
    sessions: '/student/sessions',
    history: '/student/history',
    myPage: '/student/mypage',
  },
  teacher: {
    home: '/teacher',
    students: '/teacher/students',
    homework: '/teacher/homeworks',
    analytics: '/teacher/analytics',
  },
} as const
