import RoleLayout from '@/layouts/components/RoleLayout'

export default function GuardianLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <RoleLayout role="GUARDIAN">{children}</RoleLayout>
}
