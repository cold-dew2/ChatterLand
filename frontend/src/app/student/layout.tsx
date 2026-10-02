import RoleLayout from '@/layouts/components/RoleLayout'

export default function StudentLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <RoleLayout role="STUDENT">{children}</RoleLayout>
}
