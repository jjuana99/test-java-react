import { severityLabel, type Severity } from './api'

const icon: Record<Severity, string> = { INFO: 'ℹ', WARNING: '▲', CRITICAL: '■' }

export default function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span className={`badge sev-${severity.toLowerCase()}`}>
      <span aria-hidden="true">{icon[severity]}</span> {severityLabel[severity]}
    </span>
  )
}
