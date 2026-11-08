import { authFetch } from '../auth/firebase'

export type Sex = 'FEMALE' | 'MALE' | 'UNSPECIFIED'
export type Employment = 'EMPLOYEE' | 'SELF_EMPLOYED' | 'CIVIL_SERVANT' | 'UNEMPLOYED' | 'INACTIVE'
export type RiskProfile = 'CONSERVATIVE' | 'BALANCED' | 'DYNAMIC'

export interface WorkPeriod {
  regime: string
  employer: string
  from: string
  to: string | null
  days: number
  assimilated: boolean
}

export interface WorkHistory {
  reportDate: string | null
  totalDays: number
  years: number
  months: number
  days: number
  contributionYears: number
  firstRegistration: string | null
  currentRegime: string | null
  currentlyRegistered: boolean
  address: string | null
  periods: WorkPeriod[]
  warnings: string[]
}

export interface PlanRequest {
  birthDate: string
  sex: Sex
  employment: Employment
  grossAnnualSalary: number
  contributionYears: number
  keepsContributing: boolean
  retirementAge: number | null
  currentSavings: number
  targetReplacementPct: number
  riskProfile: RiskProfile
  dependents: number
  realWageGrowthPct: number
}

export interface Pension {
  eligible: boolean
  contributionYears: number
  percentage: number
  delayBonusPct: number
  contributionBase: number
  regulatoryBase: number
  monthly: number
  annual: number
  capped: boolean
}

export interface Scenario {
  profile: RiskProfile
  retirementAge: number
  monthlySaving: number
  pensionMonthly: number
}

export interface Recommendation {
  kind: string
  title: string
  detail: string
  monthlyAmount: number | null
}

export interface RetirementPlan {
  currentAge: number
  ordinaryAge: number
  retirementAge: number
  yearsToRetirement: number
  pension: Pension
  finalSalary: number
  targetAnnualIncome: number
  gapAnnual: number
  lifeExpectancy: number
  horizonYears: number
  capitalNeeded: number
  savingsAtRetirement: number
  monthlySaving: number
  onTrack: boolean
  stressedMonthlySaving: number
  rates: {
    accumulationReal: number
    retirementReal: number
    safeNominal: number
    inflation: number
    realWageGrowth: number
    ratesDate: string
    lifeTableYear: number
  }
  annuityMonthly: number
  scenarios: Scenario[]
  projection: { age: number; year: number; balance: number }[]
  recommendations: Recommendation[]
  warnings: string[]
  bankProducts: BankProducts | null
  productPlan: ProductPlan | null
}

export interface Holding {
  productId: string
  name: string
  monthly: number
  grossRealReturn: number
  feePct: number
  feeAssumed: boolean
  netRealReturn: number
  contributions: number
  returns: number
  feesPaid: number
  finalBalance: number
}

export interface ProductPlan {
  scenario: 'BASE' | 'PRUDENCE'
  provider: string
  monthlyContribution: number
  monthlyWithoutFees: number
  holdings: Holding[]
  contributions: number
  returns: number
  feesPaid: number
  capitalAtRetirement: number
  income: { target: number; publicPension: number; fromContributions: number; fromReturns: number }
  budget: { netMonthlyIncome: number; invested: number; fees: number; taxSaving: number; leftToSpend: number }
  assumptions: string[]
}

export type ProductRole = 'PENSION_PLAN' | 'SAVINGS' | 'LUMP_SUM' | 'PAYOUT'

export interface ScoreItem {
  key: string
  label: string
  points: number
  max: number
  detail: string
}

export interface Comparison {
  winnerId: string
  winnerName: string
  winnerScore: number
  verdict: string
  losses: string[]
  gains: string[]
}

export interface ProductSuggestion {
  productId: string
  name: string
  type: string
  role: ProductRole
  roleLabel: string
  score: number
  rank: number
  breakdown: ScoreItem[]
  versusBest: Comparison | null
  recommended: boolean
  best: boolean
  monthlyAmount: number | null
  risk: number | null
  riskScale: number | null
  totalFee: number | null
  feeCostAtRetirement: number | null
  return12m: number | null
  reasons: string[]
  cautions: string[]
  description: string
  url: string
}

export interface BankProducts {
  provider: string
  verifiedOn: string
  note: string
  summary: string
  suggestions: ProductSuggestion[]
  unavailable: { name: string; reason: string }[]
}

async function parseError(res: Response) {
  try {
    const body = await res.json()
    return body.detail ?? body.message ?? `Error ${res.status}`
  } catch {
    return `Error ${res.status}`
  }
}

export async function uploadWorkHistory(file: File): Promise<WorkHistory> {
  const form = new FormData()
  form.append('file', file)
  const res = await authFetch('/api/retirement/work-history', { method: 'POST', body: form })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export async function requestPlan(req: PlanRequest): Promise<RetirementPlan> {
  const res = await authFetch('/api/retirement/plan', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export interface TypologyShare {
  code: string
  name: string | null
  group: string | null
  families: number
  share: number
  provinceShare: number
  nationalShare: number
}

export interface Neighbourhood {
  address: string
  municipality: string | null
  province: string | null
  postalCode: string | null
  cusec: string
  year: number
  families: number
  typologies: TypologyShare[]
}

/** Habits typologies of the census section of an address (sent in the body, not the URL). */
export async function requestNeighbourhood(address: string): Promise<Neighbourhood> {
  const res = await authFetch('/api/retirement/neighbourhood', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ address }),
  })
  if (!res.ok) throw new Error(await parseError(res))
  return res.json()
}

export const profileLabel: Record<RiskProfile, string> = {
  CONSERVATIVE: 'Conservador',
  BALANCED: 'Moderado',
  DYNAMIC: 'Dinámico',
}

export const employmentLabel: Record<Employment, string> = {
  EMPLOYEE: 'Asalariado/a',
  SELF_EMPLOYED: 'Autónomo/a',
  CIVIL_SERVANT: 'Funcionario/a',
  UNEMPLOYED: 'En desempleo',
  INACTIVE: 'Sin actividad',
}

const eur0 = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
export const eur = (v: number) => eur0.format(v)
export const pct = (v: number, digits = 1) =>
  `${new Intl.NumberFormat('es-ES', { maximumFractionDigits: digits }).format(v)} %`
