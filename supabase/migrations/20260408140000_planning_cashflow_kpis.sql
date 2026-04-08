-- ARCÁDIA OS: planeamento (objetivos + revisões), fluxo de caixa importado/API, KPIs enriquecidos.
-- Requer tabelas base (users, sessions, decision_history). Amplia decision_history se faltar colunas.

ALTER TABLE public.decision_history
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS financial_impact numeric(18,2),
  ADD COLUMN IF NOT EXISTS expected_value numeric(18,2);

-- ---------------------------------------------------------------------------
-- Objetivos de planeamento
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.financial_goals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  title text NOT NULL,
  description text,
  target_amount numeric(18,2),
  target_date date,
  status text NOT NULL DEFAULT 'active'
    CHECK (status IN ('active', 'paused', 'completed', 'archived')),
  review_cadence_days integer NOT NULL DEFAULT 30
    CHECK (review_cadence_days > 0 AND review_cadence_days <= 730),
  next_review_at timestamptz,
  linked_decision_id uuid REFERENCES public.decision_history (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_financial_goals_user ON public.financial_goals (user_id);
CREATE INDEX IF NOT EXISTS idx_financial_goals_next_review ON public.financial_goals (user_id, next_review_at);

-- ---------------------------------------------------------------------------
-- Revisões periódicas (ligadas ao histórico de decisões via objetivo)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.planning_reviews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  goal_id uuid NOT NULL REFERENCES public.financial_goals (id) ON DELETE CASCADE,
  reviewed_at timestamptz NOT NULL DEFAULT now(),
  notes text,
  outcome text CHECK (outcome IS NULL OR outcome IN ('on_track', 'at_risk', 'off_track', 'not_applicable')),
  next_review_at timestamptz,
  linked_decision_id uuid REFERENCES public.decision_history (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_planning_reviews_goal ON public.planning_reviews (goal_id, reviewed_at DESC);

-- ---------------------------------------------------------------------------
-- Contas e movimentos (importação CSV / API / manual)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.cash_accounts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  name text NOT NULL,
  currency text NOT NULL DEFAULT 'BRL',
  external_ref text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.cash_movements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  account_id uuid REFERENCES public.cash_accounts (id) ON DELETE SET NULL,
  occurred_at timestamptz NOT NULL,
  amount numeric(18,2) NOT NULL,
  description text,
  category text,
  source text NOT NULL DEFAULT 'import'
    CHECK (source IN ('import', 'manual', 'api')),
  raw jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cash_movements_user_time ON public.cash_movements (user_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.financial_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.planning_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cash_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cash_movements ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "financial_goals_own" ON public.financial_goals;
CREATE POLICY "financial_goals_own" ON public.financial_goals
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "planning_reviews_own" ON public.planning_reviews;
CREATE POLICY "planning_reviews_own" ON public.planning_reviews
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "cash_accounts_own" ON public.cash_accounts;
CREATE POLICY "cash_accounts_own" ON public.cash_accounts
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "cash_movements_own" ON public.cash_movements;
CREATE POLICY "cash_movements_own" ON public.cash_movements
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

GRANT ALL ON public.financial_goals TO authenticated;
GRANT ALL ON public.planning_reviews TO authenticated;
GRANT ALL ON public.cash_accounts TO authenticated;
GRANT ALL ON public.cash_movements TO authenticated;

-- ---------------------------------------------------------------------------
-- KPIs: decisões (30d) + fluxo de caixa real (30d)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.dashboard_financial_kpis ()
RETURNS TABLE (
  net_impact numeric,
  expected_impact numeric,
  execution_success_rate numeric,
  avg_confidence numeric,
  cashflow_net_30d numeric,
  decision_impact_30d numeric
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  WITH uid AS (
    SELECT auth.uid () AS id
  ),
  dec_30 AS (
    SELECT
      COALESCE(SUM(dh.financial_impact), 0) AS impact_sum,
      COALESCE(AVG(dh.confidence), 0) AS conf_avg,
      COUNT(*) FILTER (WHERE dh.status = 'executed')::numeric AS exec_n,
      NULLIF(COUNT(*), 0)::numeric AS total_n,
      COALESCE(AVG(dh.expected_value) FILTER (WHERE dh.expected_value IS NOT NULL), 0) AS exp_avg
    FROM public.decision_history dh, uid
    WHERE
      dh.user_id = uid.id
      AND dh.executed_at >= (now() - interval '30 days')
  ),
  cash_30 AS (
    SELECT
      COALESCE(SUM(cm.amount), 0) AS flow
    FROM public.cash_movements cm, uid
    WHERE
      cm.user_id = uid.id
      AND cm.occurred_at >= (now() - interval '30 days')
  ),
  goals_m AS (
    SELECT
      COALESCE(AVG(fg.target_amount) FILTER (WHERE fg.target_amount IS NOT NULL), 0) / 12.0 AS monthly_from_goals
    FROM public.financial_goals fg, uid
    WHERE
      fg.user_id = uid.id
      AND fg.status = 'active'
  )
  SELECT
    (SELECT impact_sum FROM dec_30) + (SELECT flow FROM cash_30) AS net_impact,
    GREATEST(
      (SELECT exp_avg FROM dec_30),
      (SELECT monthly_from_goals FROM goals_m)
    ) AS expected_impact,
    CASE
      WHEN (SELECT total_n FROM dec_30) IS NULL OR (SELECT total_n FROM dec_30) = 0 THEN 0::numeric
      ELSE ROUND(100.0 * (SELECT exec_n FROM dec_30) / (SELECT total_n FROM dec_30), 2)
    END AS execution_success_rate,
    ROUND((SELECT conf_avg FROM dec_30), 2) AS avg_confidence,
    (SELECT flow FROM cash_30) AS cashflow_net_30d,
    (SELECT impact_sum FROM dec_30) AS decision_impact_30d;
$$;

-- ---------------------------------------------------------------------------
-- Status do sistema (métricas operacionais)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.dashboard_system_status ()
RETURNS TABLE (
  executions_24h bigint,
  executions_7d bigint,
  precision_score numeric,
  active_autopilot_sessions bigint,
  open_risk_events bigint
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  WITH uid AS (
    SELECT auth.uid () AS id
  )
  SELECT
    (
      SELECT COUNT(*)::bigint
      FROM public.decision_history dh, uid
      WHERE
        dh.user_id = uid.id
        AND dh.executed_at >= (now() - interval '24 hours')
    ) AS executions_24h,
    (
      SELECT COUNT(*)::bigint
      FROM public.decision_history dh, uid
      WHERE
        dh.user_id = uid.id
        AND dh.executed_at >= (now() - interval '7 days')
    ) AS executions_7d,
    COALESCE(
      (
        SELECT AVG(pm.metric_value)
        FROM public.performance_metrics pm, uid
        WHERE
          pm.user_id = uid.id
          AND pm.metric_name = 'autopilot_confidence'
          AND pm.window_end >= (now() - interval '7 days')
      ),
      75::numeric
    ) AS precision_score,
    (
      SELECT COUNT(*)::bigint
      FROM public.sessions s, uid
      WHERE
        s.user_id = uid.id
        AND COALESCE(s.autopilot_enabled, false) = true
        AND s.last_activity_at >= (now() - interval '24 hours')
    ) AS active_autopilot_sessions,
    (
      SELECT COUNT(*)::bigint
      FROM public.performance_metrics pm, uid
      WHERE
        pm.user_id = uid.id
        AND pm.metric_name = 'fallback_risk_flag'
        AND COALESCE(pm.metric_value, 0) > 0
        AND pm.window_end >= (now() - interval '7 days')
    ) AS open_risk_events;
$$;

-- ---------------------------------------------------------------------------
-- Últimas decisões (para o dashboard)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.dashboard_latest_executed_decisions ()
RETURNS TABLE (
  id uuid,
  decision_type text,
  status text,
  confidence_score numeric,
  description text,
  financial_impact numeric,
  executed_at timestamptz
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    dh.id,
    dh.decision_type::text,
    dh.status::text,
    dh.confidence::numeric AS confidence_score,
    dh.description,
    dh.financial_impact,
    dh.executed_at
  FROM public.decision_history dh
  WHERE
    dh.user_id = auth.uid ()
  ORDER BY dh.executed_at DESC NULLS LAST
  LIMIT 10;
$$;

GRANT EXECUTE ON FUNCTION public.dashboard_financial_kpis () TO authenticated;
GRANT EXECUTE ON FUNCTION public.dashboard_system_status () TO authenticated;
GRANT EXECUTE ON FUNCTION public.dashboard_latest_executed_decisions () TO authenticated;
