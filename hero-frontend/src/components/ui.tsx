import type { ButtonHTMLAttributes, ReactNode } from 'react'
import type { AttendanceOutcomeValue, EventStatus, RsvpResponseValue } from '../types'

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-xl border border-slate-200 bg-white p-6 shadow-sm ${className}`}>{children}</div>
}

export type BadgeTone = 'green' | 'amber' | 'red' | 'slate' | 'indigo'

const BADGE_TONE_CLASSES: Record<BadgeTone, string> = {
  green: 'bg-green-100 text-green-800',
  amber: 'bg-amber-100 text-amber-800',
  red: 'bg-red-100 text-red-800',
  slate: 'bg-slate-100 text-slate-700',
  indigo: 'bg-indigo-100 text-indigo-800',
}

export function Badge({ tone, children }: { tone: BadgeTone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${BADGE_TONE_CLASSES[tone]}`}>
      {children}
    </span>
  )
}

export function Alert({ kind, children }: { kind: 'error' | 'success' | 'info'; children: ReactNode }) {
  const toneClass =
    kind === 'error'
      ? 'border-red-200 bg-red-50 text-red-800'
      : kind === 'success'
        ? 'border-green-200 bg-green-50 text-green-800'
        : 'border-indigo-200 bg-indigo-50 text-indigo-800'
  return <div className={`rounded-lg border px-4 py-3 text-sm ${toneClass}`}>{children}</div>
}

export function Field({
  label,
  htmlFor,
  children,
  hint,
}: {
  label: string
  htmlFor: string
  children: ReactNode
  hint?: string
}) {
  return (
    <div className="flex flex-1 flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-indigo-600 text-white hover:bg-indigo-500',
  secondary: 'bg-slate-100 text-slate-800 hover:bg-slate-200',
  danger: 'bg-red-600 text-white hover:bg-red-500',
  ghost: 'text-slate-600 hover:bg-slate-100',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  loading?: boolean
}

export function Button({ children, variant = 'primary', loading = false, className = '', disabled, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${VARIANT_CLASSES[variant]} ${className}`}
    >
      {loading ? 'Please wait…' : children}
    </button>
  )
}

export const inputClass =
  'w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500'

export function statusTone(status: EventStatus): BadgeTone {
  switch (status) {
    case 'OPEN':
      return 'green'
    case 'CLOSED':
      return 'slate'
    case 'CANCELLED':
      return 'red'
  }
}

export function responseTone(value: RsvpResponseValue): BadgeTone {
  switch (value) {
    case 'YES':
      return 'green'
    case 'NO':
      return 'red'
    case 'MAYBE':
      return 'amber'
  }
}

export function outcomeTone(value: AttendanceOutcomeValue): BadgeTone {
  switch (value) {
    case 'CONFIRMED':
      return 'green'
    case 'WAITLISTED':
      return 'amber'
    case 'NONE':
      return 'slate'
  }
}
