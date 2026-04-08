-- Perfil financeiro High-End (metas, pesos, saldo base) — Arcádia Dashboard
CREATE TABLE IF NOT EXISTS public.user_finance_profile (
  user_id uuid PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
  opening_balance numeric(18,2) NOT NULL DEFAULT 0,
  emergency_fund_target numeric(18,2) NOT NULL DEFAULT 15000,
  monthly_budget_total numeric(18,2),
  weight_invest integer NOT NULL DEFAULT 40 CHECK (weight_invest >= 0 AND weight_invest <= 100),
  weight_security integer NOT NULL DEFAULT 30 CHECK (weight_security >= 0 AND weight_security <= 100),
  weight_life integer NOT NULL DEFAULT 30 CHECK (weight_life >= 0 AND weight_life <= 100),
  autopilot_insights_enabled boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.user_finance_profile ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_finance_profile_own" ON public.user_finance_profile;
CREATE POLICY "user_finance_profile_own" ON public.user_finance_profile
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

GRANT ALL ON public.user_finance_profile TO authenticated;
