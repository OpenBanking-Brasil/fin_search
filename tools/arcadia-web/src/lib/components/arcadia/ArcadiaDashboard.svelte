<script lang="ts">
  import { onMount } from 'svelte';
  import { Button } from '$lib/components/ui/button';
  import { Input } from '$lib/components/ui/input';
  import { Label } from '$lib/components/ui/label';
  import { authUser } from '$lib/store/auth-store';
  import { activeConstitution } from '$lib/store/constitution-store';
  import { getSupabaseClient } from '$lib/services/SupabaseClient';
  import {
    aggregateByCategory,
    sumSince,
    sumDay,
    computeFinancialHealthScore,
    buildDailyInsight,
    type MovementRow,
  } from '$lib/services/FinanceMetrics';

  let loading = true;
  let error: string | null = null;

  let openingBalance = 0;
  let emergencyTarget = 15000;
  let monthlyBudget = 8000;
  let movements: MovementRow[] = [];

  let financialKpis: Record<string, number | null> | null = null;
  let systemStatus: Record<string, number | null> | null = null;
  let latestDecisions: Record<string, unknown>[] = [];

  let showIncome = false;
  let showExpense = false;
  let txAmount = '';
  let txCategory = '';
  let txDesc = '';

  $: totalBalance =
    openingBalance +
    movements.reduce((s, m) => s + Number(m.amount), 0);

  const now = new Date();
  const startMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  const d = new Date();
  d.setDate(d.getDate() - 1);
  const today = new Date();
  const weekAgo = new Date();
  weekAgo.setDate(weekAgo.getDate() - 7);

  $: monthSpend = movements
    .filter((m) => new Date(m.occurred_at) >= startMonth && Number(m.amount) < 0)
    .reduce((s, m) => s + Math.abs(Number(m.amount)), 0);
  $: budgetProgress = monthlyBudget > 0 ? Math.min(1, monthSpend / monthlyBudget) : 0;

  $: emergencyCurrent = Math.max(0, totalBalance * 0.15);
  $: emergencyRatio = emergencyTarget > 0 ? Math.min(1, emergencyCurrent / emergencyTarget) : 0;

  $: cashflow30 = sumSince(movements, new Date(Date.now() - 30 * 86400000));

  $: healthScore = computeFinancialHealthScore({
    emergencyProgress: emergencyRatio,
    budgetUsedRatio: budgetProgress,
    negativeDaysRatio: 0.2,
  });

  $: cats = aggregateByCategory(movements, startMonth);
  $: deliveryCats = aggregateByCategory(
    movements.filter((m) => /delivery|ifood|rappi/i.test(m.category ?? '')),
    weekAgo,
  );
  $: deliveryWeek = deliveryCats.reduce((s, c) => s + Math.abs(c.total), 0);
  $: deliveryAvg = deliveryWeek / 1 || 0;
  $: insight = buildDailyInsight({
    categoryTotals: cats,
    deliveryRollingAvg: Math.max(1, deliveryAvg),
    deliveryThisWeek: deliveryWeek,
  });

  $: dayToday = sumDay(movements as MovementRow[], today);
  $: dayYesterday = sumDay(movements as MovementRow[], d);
  $: dayVarPct =
    dayYesterday !== 0 ? ((dayToday - dayYesterday) / Math.abs(dayYesterday)) * 100 : 0;

  const maxCat = () => (cats.length ? Math.max(...cats.map((c) => Math.abs(c.total)), 1) : 1);

  async function load() {
    if (!$authUser) return;
    const supabase = getSupabaseClient();
    if (!supabase) {
      error = 'Sem Supabase';
      loading = false;
      return;
    }
    loading = true;
    error = null;
    try {
      const { data: prof, error: pe } = await supabase
        .from('user_finance_profile')
        .select('*')
        .eq('user_id', $authUser.id)
        .maybeSingle();
      if (pe && pe.code !== 'PGRST116') throw pe;

      if (!prof) {
        await supabase.from('user_finance_profile').insert({
          user_id: $authUser.id,
          opening_balance: 0,
          emergency_fund_target: 15000,
          monthly_budget_total: 8000,
          weight_invest: 40,
          weight_security: 30,
          weight_life: 30,
        });
        openingBalance = 0;
        emergencyTarget = 15000;
        monthlyBudget = 8000;
      } else {
        openingBalance = Number(prof.opening_balance ?? 0);
        emergencyTarget = Number(prof.emergency_fund_target ?? 15000);
        monthlyBudget = Number(prof.monthly_budget_total ?? 8000);
      }

      const { data: mv, error: me } = await supabase
        .from('cash_movements')
        .select('amount, occurred_at, category, description')
        .eq('user_id', $authUser.id)
        .order('occurred_at', { ascending: false })
        .limit(2000);
      if (me) throw me;
      movements = (mv ?? []) as MovementRow[];

      const [k, s, dec] = await Promise.all([
        supabase.rpc('dashboard_financial_kpis'),
        supabase.rpc('dashboard_system_status'),
        supabase.rpc('dashboard_latest_executed_decisions'),
      ]);
      if (k.error) throw k.error;
      if (s.error) throw s.error;
      if (dec.error) throw dec.error;
      financialKpis = k.data?.[0] ?? null;
      systemStatus = s.data?.[0] ?? null;
      latestDecisions = (dec.data ?? []) as Record<string, unknown>[];
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  }

  async function saveTx(kind: 'in' | 'out') {
    const supabase = getSupabaseClient();
    if (!supabase || !$authUser) return;
    const raw = parseFloat(txAmount.replace(',', '.'));
    if (!Number.isFinite(raw) || raw === 0) return;
    const amount = kind === 'in' ? Math.abs(raw) : -Math.abs(raw);
    const { error: e } = await supabase.from('cash_movements').insert({
      user_id: $authUser.id,
      occurred_at: new Date().toISOString(),
      amount,
      category: txCategory.trim() || (kind === 'in' ? 'entrada' : 'geral'),
      description: txDesc.trim() || (kind === 'in' ? 'Entrada' : 'Gasto'),
      source: 'manual',
    });
    if (e) {
      error = e.message;
      return;
    }
    showIncome = false;
    showExpense = false;
    txAmount = '';
    txCategory = '';
    txDesc = '';
    await load();
  }

  onMount(load);

  function fmtBrl(n: number) {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(n);
  }
</script>

<div
  class="min-h-screen bg-[#0b0f14] text-slate-100 font-[Inter,system-ui,sans-serif] pb-24 md:pb-8"
>
  <div class="max-w-6xl mx-auto px-4 pt-6 space-y-8">
    <header class="flex flex-col md:flex-row md:items-end md:justify-between gap-4">
      <div>
        <p class="text-xs uppercase tracking-[0.2em] text-[#00D4A5]/90 mb-1">Arcádia Dashboard</p>
        <h1 class="text-2xl md:text-3xl font-semibold text-white">
          ARCÁDIA OS <span class="text-[#7C3AED]">Finanças Pessoais</span> High-End
        </h1>
        <p class="text-sm text-slate-400 mt-1">
          {#if $activeConstitution}
            Constituição: {$activeConstitution.title} · v{$activeConstitution.version}
          {:else}
            Carregando constituição…
          {/if}
        </p>
      </div>
      <div class="text-right">
        <p class="text-xs text-slate-500">Saldo total estimado</p>
        <p class="text-3xl md:text-4xl font-bold tabular-nums text-[#00D4A5]">
          {loading ? '…' : fmtBrl(totalBalance)}
        </p>
        <p class="text-sm mt-1 {dayVarPct >= 0 ? 'text-emerald-400' : 'text-rose-400'}">
          Variação hoje vs ontem: {dayVarPct >= 0 ? '+' : ''}{dayVarPct.toFixed(1)}% · {fmtBrl(
            dayToday,
          )}
        </p>
      </div>
    </header>

    {#if error}
      <div class="rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
        {error}
      </div>
    {/if}

    <!-- FABs -->
    <div class="fixed bottom-24 right-4 z-40 flex flex-col gap-3 md:bottom-8">
      <button
        type="button"
        class="rounded-full bg-[#00D4A5] text-slate-950 font-semibold px-5 py-3 shadow-lg shadow-[#00D4A5]/25 hover:opacity-95"
        on:click={() => {
          showIncome = true;
          showExpense = false;
        }}
      >
        + Entrada
      </button>
      <button
        type="button"
        class="rounded-full bg-[#7C3AED] text-white font-semibold px-5 py-3 shadow-lg shadow-[#7C3AED]/30"
        on:click={() => {
          showExpense = true;
          showIncome = false;
        }}
      >
        – Gasto
      </button>
    </div>

    {#if showIncome || showExpense}
      <div
        class="fixed inset-0 z-50 flex items-end md:items-center justify-center bg-black/60 p-4"
        role="presentation"
        on:click|self={() => {
          showIncome = false;
          showExpense = false;
        }}
      >
        <div
          class="w-full max-w-md rounded-2xl border border-white/10 bg-[#12171f] p-6 space-y-4"
          role="dialog"
        >
          <h3 class="text-lg font-semibold">
            {showIncome ? 'Nova entrada' : 'Novo gasto'}
          </h3>
          <div class="space-y-2">
            <Label for="amt">Valor (R$)</Label>
            <Input id="amt" bind:value={txAmount} placeholder="0,00" class="bg-slate-900/80 border-slate-600" />
          </div>
          <div class="space-y-2">
            <Label for="cat">Categoria</Label>
            <Input id="cat" bind:value={txCategory} placeholder="ex.: alimentação, delivery" class="bg-slate-900/80 border-slate-600" />
          </div>
          <div class="space-y-2">
            <Label for="dsc">Descrição</Label>
            <Input id="dsc" bind:value={txDesc} placeholder="opcional" class="bg-slate-900/80 border-slate-600" />
          </div>
          <div class="flex gap-2 justify-end">
            <Button variant="ghost" on:click={() => { showIncome = false; showExpense = false; }}>Cancelar</Button>
            <Button on:click={() => saveTx(showIncome ? 'in' : 'out')}>Guardar</Button>
          </div>
        </div>
      </div>
    {/if}

    <!-- 4 cards -->
    <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
      <div class="rounded-2xl border border-white/10 bg-gradient-to-br from-white/5 to-transparent p-5">
        <p class="text-xs text-slate-400 uppercase tracking-wide">Orçamento mensal</p>
        <div class="mt-3 flex items-center gap-4">
          <div
            class="relative h-16 w-16 rounded-full"
            style="background: conic-gradient(#00D4A5 {budgetProgress * 360}deg, #1e293b 0deg);"
          >
            <div class="absolute inset-2 rounded-full bg-[#0b0f14] flex items-center justify-center text-xs font-bold">
              {Math.round(budgetProgress * 100)}%
            </div>
          </div>
          <div class="text-sm text-slate-300">
            <div>Gasto: {fmtBrl(monthSpend)}</div>
            <div class="text-slate-500">Meta: {fmtBrl(monthlyBudget)}</div>
          </div>
        </div>
      </div>

      <div class="rounded-2xl border border-white/10 bg-gradient-to-br from-[#7C3AED]/20 to-transparent p-5">
        <p class="text-xs text-slate-400 uppercase tracking-wide">Reserva de emergência</p>
        <p class="text-2xl font-bold text-white mt-2">{Math.round(emergencyRatio * 100)}%</p>
        <div class="h-2 rounded-full bg-slate-800 mt-3 overflow-hidden">
          <div
            class="h-full bg-[#7C3AED] transition-all"
            style="width: {emergencyRatio * 100}%"
          ></div>
        </div>
        <p class="text-xs text-slate-500 mt-2">
          Estimativa atual vs meta {fmtBrl(emergencyTarget)}
        </p>
      </div>

      <div class="rounded-2xl border border-white/10 bg-white/5 p-5">
        <p class="text-xs text-slate-400 uppercase tracking-wide">Fluxo 30 dias</p>
        <p class="text-2xl font-bold mt-2 text-[#00D4A5]">{fmtBrl(cashflow30)}</p>
        <p class="text-xs text-slate-500 mt-1">Soma dos movimentos (últimos 30d)</p>
      </div>

      <div class="rounded-2xl border border-white/10 bg-white/5 p-5">
        <p class="text-xs text-slate-400 uppercase tracking-wide">Score de saúde</p>
        <p class="text-4xl font-black mt-1 bg-gradient-to-r from-[#00D4A5] to-[#7C3AED] bg-clip-text text-transparent">
          {healthScore}
        </p>
        <p class="text-xs text-slate-500 mt-1">Heurística Arcádia (educativo)</p>
      </div>
    </div>

    <!-- Insight -->
    <div class="rounded-2xl border border-[#00D4A5]/30 bg-[#00D4A5]/5 p-5 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
      <div>
        <p class="text-xs font-semibold text-[#00D4A5] uppercase">Insight do dia</p>
        <p class="text-lg text-white mt-1">{insight.body}</p>
        {#if insight.action}
          <p class="text-sm text-slate-400 mt-2">{insight.action}</p>
        {/if}
      </div>
      <Button
        variant="ghost"
        class="border border-[#7C3AED]/50 text-[#c4b5fd] shrink-0"
        on:click={() => (window.location.href = '/constitution')}
      >
        Modo disciplina · Constituição
      </Button>
    </div>

    <!-- Charts row -->
    <div class="grid lg:grid-cols-2 gap-6">
      <div class="rounded-2xl border border-white/10 p-5 bg-[#12171f]">
        <h3 class="text-sm font-semibold text-slate-300 mb-4">Gastos por categoria (mês)</h3>
        <div class="space-y-3">
          {#each cats.slice(0, 8) as c}
            <div>
              <div class="flex justify-between text-xs text-slate-400 mb-1">
                <span>{c.name}</span>
                <span>{fmtBrl(c.total)}</span>
              </div>
              <div class="h-2 rounded-full bg-slate-800 overflow-hidden">
                <div
                  class="h-full bg-gradient-to-r from-[#7C3AED] to-[#00D4A5]"
                  style="width: {(Math.abs(c.total) / maxCat()) * 100}%"
                ></div>
              </div>
            </div>
          {:else}
            <p class="text-sm text-slate-500">Sem dados — registe movimentos ou importe CSV em Planeamento.</p>
          {/each}
        </div>
      </div>

      <div class="rounded-2xl border border-white/10 p-5 bg-[#12171f]">
        <h3 class="text-sm font-semibold text-slate-300 mb-4">Gastos vs orçamento (mês)</h3>
        <div class="flex items-end gap-2 h-40">
          <div class="flex-1 flex flex-col justify-end">
            <div
              class="w-full rounded-t-lg bg-slate-700"
              style="height: {Math.min(100, (monthSpend / Math.max(monthlyBudget, 1)) * 100)}%"
            ></div>
            <p class="text-[10px] text-center text-slate-500 mt-2">Real</p>
          </div>
          <div class="flex-1 flex flex-col justify-end">
            <div class="w-full rounded-t-lg bg-[#00D4A5]/40" style="height: 100%"></div>
            <p class="text-[10px] text-center text-slate-500 mt-2">Orçamento</p>
          </div>
        </div>
        <p class="text-xs text-slate-500 mt-4">
          Evolução patrimonial detalhada: use Relatórios ou exporte movimentos.
        </p>
      </div>
    </div>

    <!-- KPIs legacy + decisões -->
    {#if financialKpis}
      <div class="grid md:grid-cols-4 gap-3 text-sm">
        <div class="rounded-xl border border-white/10 p-3">
          <p class="text-slate-500 text-xs">Impacto líquido 30d</p>
          <p class="text-[#00D4A5] font-semibold">{fmtBrl(Number(financialKpis.net_impact ?? 0))}</p>
        </div>
        <div class="rounded-xl border border-white/10 p-3">
          <p class="text-slate-500 text-xs">Taxa sucesso</p>
          <p class="text-white font-semibold">{Number(financialKpis.execution_success_rate ?? 0).toFixed(0)}%</p>
        </div>
        <div class="rounded-xl border border-white/10 p-3">
          <p class="text-slate-500 text-xs">Confiança média</p>
          <p class="text-white font-semibold">{Number(financialKpis.avg_confidence ?? 0).toFixed(0)}%</p>
        </div>
        <div class="rounded-xl border border-white/10 p-3">
          <p class="text-slate-500 text-xs">Fluxo caixa 30d</p>
          <p class="text-[#7C3AED] font-semibold">{fmtBrl(Number(financialKpis.cashflow_net_30d ?? 0))}</p>
        </div>
      </div>
    {/if}

    {#if systemStatus}
      <div class="rounded-xl border border-white/10 p-4 flex flex-wrap gap-6 text-xs text-slate-400">
        <span>Exec. 24h: <strong class="text-white">{systemStatus.executions_24h ?? 0}</strong></span>
        <span>Exec. 7d: <strong class="text-white">{systemStatus.executions_7d ?? 0}</strong></span>
        <span>Precisão: <strong class="text-white">{Number(systemStatus.precision_score ?? 0).toFixed(0)}%</strong></span>
        <span>Riscos: <strong class="text-rose-300">{systemStatus.open_risk_events ?? 0}</strong></span>
      </div>
    {/if}

    {#if latestDecisions.length}
      <div class="rounded-2xl border border-white/10 p-5">
        <h3 class="text-sm font-semibold text-slate-300 mb-3">Últimas decisões do Parlamento</h3>
        <ul class="space-y-2 text-sm">
          {#each latestDecisions.slice(0, 5) as dec}
            <li class="flex justify-between gap-4 border-b border-white/5 pb-2">
              <span class="text-slate-300">{(dec.decision_type as string) ?? '—'}</span>
              <span class="text-slate-500 text-xs">{new Date(String(dec.executed_at)).toLocaleString('pt-BR')}</span>
            </li>
          {/each}
        </ul>
      </div>
    {/if}

    <div class="flex flex-wrap gap-3">
      <Button variant="ghost" class="border border-white/15" on:click={() => (window.location.href = '/planning')}>Planeamento</Button>
      <Button variant="ghost" class="border border-white/15" on:click={() => (window.location.href = '/reports')}>Relatórios</Button>
      <Button variant="ghost" class="border border-white/15" on:click={() => (window.location.href = '/chat')}>Assistente CFO</Button>
      <Button variant="ghost" class="border border-white/15" on:click={() => (window.location.href = '/constitution')}>Constituição Viva</Button>
      <Button variant="ghost" on:click={load}>Atualizar</Button>
    </div>
  </div>
</div>
