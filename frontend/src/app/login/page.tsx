import LoginPage from '@/features/auth/pages/LoginPage'

export default async function Login({ searchParams }: { searchParams: Promise<{ expired?: string; next?: string }> }) {
  const { expired, next } = await searchParams
  return <LoginPage initialStep="login" sessionExpired={expired === '1'} returnTo={typeof next === 'string' ? next : undefined} />
}
