import type { ReactNode } from 'react'

type EmptyStateProps = {
  title: string
  description?: string
  variant?: 'card' | 'plain'
  icon?: ReactNode
  action?: ReactNode
}

export default function EmptyState({ title, description, variant = 'card', icon, action }: EmptyStateProps) {
  const className = variant === 'plain'
    ? 'text-center py-12'
    : 'rounded-2xl border border-dashed border-gray-200 bg-white px-5 py-8 text-center'

  return <div className={className}>
    {icon}
    <p className={variant === 'plain' ? 'text-sm text-gray-400' : 'text-sm font-bold text-gray-700'}>{title}</p>
    {description && <p className="mt-1 text-xs text-gray-400">{description}</p>}
    {action && <div className="mt-3">{action}</div>}
  </div>
}
