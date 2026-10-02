import LoginPage from '@/features/auth/pages/LoginPage'

export default async function Login({ searchParams }: { searchParams: Promise<{ expired?: string }> }) {
  const { expired } = await searchParams
  return <LoginPage initialStep="login" sessionExpired={expired === '1'} />
}
