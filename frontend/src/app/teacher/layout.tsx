import RoleLayout from '@/layouts/components/RoleLayout'

export default function TeacherLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <RoleLayout role="TEACHER">{children}</RoleLayout>
}
