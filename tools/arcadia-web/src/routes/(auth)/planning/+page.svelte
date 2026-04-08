<script lang="ts">
  import { onMount } from 'svelte';
  import { Button } from '$lib/components/ui/button';
  import { Input } from '$lib/components/ui/input';
  import { Textarea } from '$lib/components/ui/textarea';
  import { Label } from '$lib/components/ui/label';
  import { authUser } from '$lib/store/auth-store';
  import { getSupabaseClient } from '$lib/services/SupabaseClient';
  import { parseMovementsCsv } from '$lib/utils/csvImport';

  type Goal = {
    id: string;
    title: string;
    description: string | null;
    target_amount: number | null;
    target_date: string | null;
    status: string;
    review_cadence_days: number;
    next_review_at: string | null;
    linked_decision_id: string | null;
  };

  type Review = {
    id: string;
    goal_id: string;
    reviewed_at: string;
    notes: string | null;
    outcome: string | null;
    next_review_at: string | null;
  };

  type Account = { id: string; name: string; currency: string };

  let loading = true;
  let error: string | null = null;
  let tab: 'goals' | 'import' | 'reviews' = 'goals';

  let goals: Goal[] = [];
  let reviews: Review[] = [];
  let accounts: Account[] = [];
  let decisions: { id: string; decision_type: string; executed_at: string }[] = [];

  let newTitle = '';
  let newDescription = '';
  let newTargetAmount = '';
  let newTargetDate = '';
  let newCadence = 30;
  let newLinkedDecision: string | null = null;

  let reviewGoalId = '';
  let reviewNotes = '';
  let reviewOutcome: 'on_track' | 'at_risk' | 'off_track' | 'not_applicable' = 'on_track';

  let importAccountName = 'Principal';
  let csvText = '';
  let importMsg: string | null = null;

  async function loadAll() {
    if (!$authUser) return;
    const supabase = getSupabaseClient();
    if (!supabase) {
      error = 'Sem ligação ao Supabase.';
      loading = false;
      return;
    }
    loading = true;
    error = null;
    try {
      const [g, r, a, d] = await Promise.all([
        supabase
          .from('financial_goals')
          .select('*')
          .eq('user_id', $authUser.id)
          .order('created_at', { ascending: false }),
        supabase
          .from('planning_reviews')
          .select('*')
          .eq('user_id', $authUser.id)
          .order('reviewed_at', { ascending: false })
          .limit(80),
        supabase.from('cash_accounts').select('id, name, currency').eq('user_id', $authUser.id),
        supabase
          .from('decision_history')
          .select('id, decision_type, executed_at')
          .eq('user_id', $authUser.id)
          .order('executed_at', { ascending: false })
          .limit(40),
      ]);
      if (g.error) throw g.error;
      if (r.error) throw r.error;
      if (a.error) throw a.error;
      if (d.error) throw d.error;
      goals = (g.data ?? []) as Goal[];
      reviews = (r.data ?? []) as Review[];
      accounts = (a.data ?? []) as Account[];
      decisions = (d.data ?? []) as typeof decisions;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  }

  async function ensureDefaultAccount(): Promise<string | null> {
    const supabase = getSupabaseClient();
    if (!supabase || !$authUser) return null;
    if (accounts.length > 0) return accounts[0].id;
    const { data, error: insErr } = await supabase
      .from('cash_accounts')
      .insert({
        user_id: $authUser.id,
        name: importAccountName.trim() || 'Principal',
        currency: 'BRL',
      })
      .select('id')
      .single();
    if (insErr) return null;
    await loadAll();
    return data?.id ?? null;
  }

  async function addGoal() {
    const supabase = getSupabaseClient();
    if (!supabase || !$authUser || !newTitle.trim()) return;
    const next = new Date();
    next.setDate(next.getDate() + newCadence);
    const { error: e } = await supabase.from('financial_goals').insert({
      user_id: $authUser.id,
      title: newTitle.trim(),
      description: newDescription.trim() || null,
      target_amount: newTargetAmount ? Number(newTargetAmount.replace(',', '.')) : null,
      target_date: newTargetDate || null,
      status: 'active',
      review_cadence_days: Math.min(730, Math.max(7, Number(newCadence) || 30)),
      next_review_at: next.toISOString(),
      linked_decision_id: newLinkedDecision || null,
    });
    if (e) {
      error = e.message;
      return;
    }
    newTitle = '';
    newDescription = '';
    newTargetAmount = '';
    newTargetDate = '';
    newLinkedDecision = null;
    await loadAll();
  }

  async function addReview() {
    const supabase = getSupabaseClient();
    if (!supabase || !$authUser || !reviewGoalId) return;
    const goal = goals.find((x) => x.id === reviewGoalId);
    const next = new Date();
    if (goal) next.setDate(next.getDate() + goal.review_cadence_days);
    const { error: e } = await supabase.from('planning_reviews').insert({
      user_id: $authUser.id,
      goal_id: reviewGoalId,
      notes: reviewNotes.trim() || null,
      outcome: reviewOutcome,
      next_review_at: next.toISOString(),
    });
    if (e) {
      error = e.message;
      return;
    }
    await supabase
      .from('financial_goals')
      .update({ next_review_at: next.toISOString(), updated_at: new Date().toISOString() })
      .eq('id', reviewGoalId)
      .eq('user_id', $authUser.id);
    reviewNotes = '';
    await loadAll();
  }

  async function importCsv() {
    importMsg = null;
    const supabase = getSupabaseClient();
    if (!supabase || !$authUser) return;
    const accountId = await ensureDefaultAccount();
    if (!accountId) {
      importMsg = 'Não foi possível criar conta padrão.';
      return;
    }
    const { rows, errors } = parseMovementsCsv(csvText);
    if (errors.length && rows.length === 0) {
      importMsg = errors.slice(0, 5).join(' ');
      return;
    }
    const payload = rows.map((r) => ({
      user_id: $authUser.id,
      account_id: accountId,
      occurred_at: r.occurredAt.toISOString(),
      amount: r.amount,
      description: r.description,
      category: r.category,
      source: 'import' as const,
    }));
    const { error: e } = await supabase.from('cash_movements').insert(payload);
    if (e) {
      importMsg = e.message;
      return;
    }
    importMsg = `Importados ${payload.length} movimentos.${errors.length ? ` Avisos: ${errors.slice(0, 3).join('; ')}` : ''}`;
    csvText = '';
    await loadAll();
  }

  function goalTitle(id: string) {
    return goals.find((g) => g.id === id)?.title ?? id;
  }

  onMount(loadAll);
</script>

<svelte:head>
  <title>Planeamento - ARCÁDIA OS</title>
</svelte:head>

<div class="min-h-screen max-w-5xl mx-auto p-4 space-y-6">
  <div class="flex flex-wrap items-center justify-between gap-4">
    <div>
      <h1 class="text-3xl font-bold text-primary-500">Planeamento financeiro</h1>
      <p class="text-surface-600 text-sm mt-1">
        Objetivos, revisões periódicas e importação de movimentos para KPIs reais no dashboard.
      </p>
    </div>
    <div class="flex gap-2 flex-wrap">
      <Button variant="ghost" on:click={() => (window.location.href = '/dashboard')}>Dashboard</Button>
      <Button variant="ghost" on:click={() => (window.location.href = '/history')}>Histórico</Button>
      <Button variant="ghost" on:click={loadAll} disabled={loading}>Atualizar</Button>
    </div>
  </div>

  {#if loading}
    <p class="text-surface-500">A carregar…</p>
  {:else if error}
    <div class="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-red-300 text-sm">
      {error}
      <p class="mt-2 text-xs opacity-80">
        Confirme que aplicou a migração <code class="bg-black/20 px-1 rounded">20260408140000_planning_cashflow_kpis.sql</code> no Supabase.
      </p>
    </div>
  {:else}
    <div class="flex gap-2 border-b border-surface-200 dark:border-surface-700 pb-2">
      <button
        type="button"
        class="px-4 py-2 rounded-t-lg text-sm font-medium {tab === 'goals' ? 'bg-primary-500/20 text-primary-300' : 'text-surface-500'}"
        on:click={() => (tab = 'goals')}
      >
        Objetivos
      </button>
      <button
        type="button"
        class="px-4 py-2 rounded-t-lg text-sm font-medium {tab === 'import' ? 'bg-primary-500/20 text-primary-300' : 'text-surface-500'}"
        on:click={() => (tab = 'import')}
      >
        Importar CSV
      </button>
      <button
        type="button"
        class="px-4 py-2 rounded-t-lg text-sm font-medium {tab === 'reviews' ? 'bg-primary-500/20 text-primary-300' : 'text-surface-500'}"
        on:click={() => (tab = 'reviews')}
      >
        Revisões
      </button>
    </div>

    {#if tab === 'goals'}
      <section class="grid md:grid-cols-2 gap-6">
        <div class="space-y-4 rounded-xl border border-surface-200 dark:border-surface-700 p-6 bg-surface-50/50 dark:bg-surface-900/40">
          <h2 class="text-lg font-semibold">Novo objetivo</h2>
          <div class="space-y-2">
            <Label for="title">Título</Label>
            <Input id="title" bind:value={newTitle} placeholder="Ex.: Reserva de emergência" />
          </div>
          <div class="space-y-2">
            <Label for="desc">Descrição</Label>
            <Textarea id="desc" bind:value={newDescription} rows={3} placeholder="Contexto e critérios de sucesso" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-2">
              <Label for="amt">Valor alvo (R$)</Label>
              <Input id="amt" type="text" bind:value={newTargetAmount} placeholder="15000" />
            </div>
            <div class="space-y-2">
              <Label for="dt">Data alvo</Label>
              <Input id="dt" type="date" bind:value={newTargetDate} />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-2">
              <Label for="cad">Revisão a cada (dias)</Label>
              <Input id="cad" type="number" min="7" max="365" bind:value={newCadence} />
            </div>
            <div class="space-y-2">
              <Label for="dec">Decisão relacionada (opcional)</Label>
              <select
                id="dec"
                class="w-full rounded-md border border-surface-600 bg-surface-800 px-3 py-2 text-sm"
                bind:value={newLinkedDecision}
              >
                <option value="">— Nenhuma —</option>
                {#each decisions as d}
                  <option value={d.id}>{d.decision_type} · {new Date(d.executed_at).toLocaleDateString('pt-BR')}</option>
                {/each}
              </select>
            </div>
          </div>
          <Button on:click={addGoal} disabled={!newTitle.trim()}>Guardar objetivo</Button>
        </div>

        <div class="space-y-3">
          <h2 class="text-lg font-semibold">Os teus objetivos</h2>
          {#if goals.length === 0}
            <p class="text-surface-500 text-sm">Ainda não há objetivos. Cria o primeiro ao lado.</p>
          {:else}
            <ul class="space-y-3">
              {#each goals as g}
                <li class="rounded-lg border border-surface-200 dark:border-surface-700 p-4">
                  <div class="font-medium">{g.title}</div>
                  {#if g.description}
                    <p class="text-sm text-surface-500 mt-1">{g.description}</p>
                  {/if}
                  <div class="text-xs text-surface-500 mt-2 flex flex-wrap gap-3">
                    {#if g.target_amount != null}
                      <span>Alvo: {g.target_amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</span>
                    {/if}
                    {#if g.target_date}
                      <span>Prazo: {g.target_date}</span>
                    {/if}
                    <span>Revisão a cada {g.review_cadence_days} d</span>
                    {#if g.next_review_at}
                      <span>Próxima: {new Date(g.next_review_at).toLocaleString('pt-BR')}</span>
                    {/if}
                  </div>
                </li>
              {/each}
            </ul>
          {/if}
        </div>
      </section>
    {/if}

    {#if tab === 'import'}
      <section class="rounded-xl border border-surface-200 dark:border-surface-700 p-6 space-y-4 bg-surface-50/50 dark:bg-surface-900/40 max-w-2xl">
        <h2 class="text-lg font-semibold">Importar movimentos (CSV)</h2>
        <p class="text-sm text-surface-500">
          Colunas: <code class="bg-black/20 px-1 rounded">data</code> (ISO ou DD/MM/AAAA),
          <code class="bg-black/20 px-1 rounded">valor</code> (positivo entrada, negativo saída),
          <code class="bg-black/20 px-1 rounded">descrição</code>,
          opcionalmente <code class="bg-black/20 px-1 rounded">categoria</code> e <code class="bg-black/20 px-1 rounded">conta</code>.
          Separador: vírgula ou ponto e vírgula.
        </p>
        <div class="space-y-2">
          <Label for="accname">Nome da conta (se ainda não existir)</Label>
          <Input id="accname" bind:value={importAccountName} placeholder="Principal" />
        </div>
        <div class="space-y-2">
          <Label for="csv">Cole o CSV</Label>
          <Textarea id="csv" bind:value={csvText} rows={12} class="font-mono text-xs" placeholder={`data,valor,descrição,categoria\n2026-01-15,-120.50,Supermercado,alimentação\n2026-01-16,5000.00,Salário,rendimento`} />
        </div>
        {#if importMsg}
          <p class="text-sm text-primary-300">{importMsg}</p>
        {/if}
        <Button on:click={importCsv} disabled={!csvText.trim()}>Importar para fluxo de caixa</Button>
        <p class="text-xs text-surface-500">
          Os valores entram no KPI <strong>Impacto líquido</strong> (30 dias) junto com impactos registados em decisões.
          Integração API: ver <code class="bg-black/20 px-1 rounded">POST /api/finance/ingest</code> e variáveis no <code class="bg-black/20 px-1 rounded">env.example</code>.
        </p>
      </section>
    {/if}

    {#if tab === 'reviews'}
      <section class="grid md:grid-cols-2 gap-6">
        <div class="space-y-4 rounded-xl border border-surface-200 dark:border-surface-700 p-6 bg-surface-50/50 dark:bg-surface-900/40">
          <h2 class="text-lg font-semibold">Registar revisão</h2>
          <div class="space-y-2">
            <Label for="gid">Objetivo</Label>
            <select
              id="gid"
              class="w-full rounded-md border border-surface-600 bg-surface-800 px-3 py-2 text-sm"
              bind:value={reviewGoalId}
            >
              <option value="">— Escolher —</option>
              {#each goals as g}
                <option value={g.id}>{g.title}</option>
              {/each}
            </select>
          </div>
          <div class="space-y-2">
            <Label for="out">Estado</Label>
            <select
              id="out"
              class="w-full rounded-md border border-surface-600 bg-surface-800 px-3 py-2 text-sm"
              bind:value={reviewOutcome}
            >
              <option value="on_track">No rumo</option>
              <option value="at_risk">Em risco</option>
              <option value="off_track">Fora do rumo</option>
              <option value="not_applicable">N/A</option>
            </select>
          </div>
          <div class="space-y-2">
            <Label for="notes">Notas</Label>
            <Textarea id="notes" bind:value={reviewNotes} rows={5} placeholder="O que mudou desde a última revisão?" />
          </div>
          <Button on:click={addReview} disabled={!reviewGoalId}>Guardar revisão</Button>
        </div>

        <div>
          <h2 class="text-lg font-semibold mb-3">Histórico de revisões</h2>
          {#if reviews.length === 0}
            <p class="text-surface-500 text-sm">Ainda não há revisões registadas.</p>
          {:else}
            <ul class="space-y-3 max-h-[480px] overflow-y-auto pr-1">
              {#each reviews as rv}
                <li class="rounded-lg border border-surface-200 dark:border-surface-700 p-3 text-sm">
                  <div class="font-medium">{goalTitle(rv.goal_id)}</div>
                  <div class="text-xs text-surface-500 mt-1">
                    {new Date(rv.reviewed_at).toLocaleString('pt-BR')}
                    {#if rv.outcome}
                      · {rv.outcome}
                    {/if}
                  </div>
                  {#if rv.notes}
                    <p class="mt-2 text-surface-600 dark:text-surface-300 whitespace-pre-wrap">{rv.notes}</p>
                  {/if}
                </li>
              {/each}
            </ul>
          {/if}
        </div>
      </section>
    {/if}
  {/if}
</div>
