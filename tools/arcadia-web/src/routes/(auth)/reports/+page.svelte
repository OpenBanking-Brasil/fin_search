<script lang="ts">
  import { Button } from '$lib/components/ui/button';
  import { Input } from '$lib/components/ui/input';
  import { Label } from '$lib/components/ui/label';
  import { authUser } from '$lib/store/auth-store';
  import { getSupabaseClient } from '$lib/services/SupabaseClient';
  import { onMount } from 'svelte';

  let scenarioPct = 15;
  let loading = false;
  let monthlyNet = 3500;
  let stressNote =
    'Correlacione picos de gastos em lazer com semanas de maior carga laboral — padrão típico de compensação emocional.';

  onMount(async () => {
    if (!$authUser) return;
    const supabase = getSupabaseClient();
    if (!supabase) return;
    const { data } = await supabase
      .from('cash_movements')
      .select('amount')
      .eq('user_id', $authUser.id)
      .gte('occurred_at', new Date(Date.now() - 30 * 86400000).toISOString());
    if (data?.length) {
      monthlyNet = data.reduce((s, r) => s + Number(r.amount), 0);
    }
  });

  $: projected = monthlyNet * (1 + scenarioPct / 100);
</script>

<svelte:head>
  <title>Relatórios & Análise — ARCÁDIA OS</title>
</svelte:head>

<div class="min-h-screen max-w-4xl mx-auto p-4 space-y-10 text-slate-100">
  <header>
    <p class="text-xs uppercase tracking-widest text-[#00D4A5] mb-1">Arcádia · Finanças Pessoais High-End</p>
    <h1 class="text-3xl font-bold text-white">Relatórios & Análise</h1>
    <p class="text-slate-400 mt-2">
      Relatórios mensais automáticos (dados dos movimentos), análise comportamental educativa e simulador de cenários.
    </p>
  </header>

  <section class="rounded-2xl border border-white/10 bg-[#12171f] p-6 space-y-3">
    <h2 class="text-lg font-semibold text-[#7C3AED]">Relatório mensal (resumo)</h2>
    <p class="text-sm text-slate-400">
      O Arcádia agrega os teus <code class="text-[#00D4A5]">cash_movements</code> e decisões do Parlamento. Exporta CSV a partir do
      Planeamento ou conecta a API de ingestão para dados em tempo real.
    </p>
    <Button variant="ghost" class="border border-white/20" on:click={() => (window.location.href = '/planning')}>
      Ir para importação / planeamento
    </Button>
  </section>

  <section class="rounded-2xl border border-white/10 bg-[#12171f] p-6 space-y-3">
    <h2 class="text-lg font-semibold text-[#00D4A5]">Análise comportamental</h2>
    <p class="text-sm text-slate-300 leading-relaxed">{stressNote}</p>
    <p class="text-xs text-slate-500">
      Em produção, cruza-se histórico de gastos com check-ins opcionais (humor/stress) — campo preparado na roadmap.
    </p>
  </section>

  <section class="rounded-2xl border border-white/10 bg-[#12171f] p-6 space-y-4">
    <h2 class="text-lg font-semibold text-white">Simulador de cenários</h2>
    <p class="text-sm text-slate-400">
      Projeção linear simples sobre o fluxo líquido dos últimos 30 dias registados.
    </p>
    <div class="flex flex-wrap items-end gap-4">
      <div class="space-y-2">
        <Label for="pct">Aumentar aportes / reduzir saídas em (%)</Label>
        <Input
          id="pct"
          type="number"
          bind:value={scenarioPct}
          class="max-w-[120px] bg-slate-900 border-slate-600"
        />
      </div>
      <div class="text-sm">
        <p class="text-slate-500">Fluxo base ~30d</p>
        <p class="text-xl font-bold text-[#00D4A5]">{monthlyNet.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</p>
        <p class="text-slate-500 mt-2">Cenário ajustado</p>
        <p class="text-xl font-bold text-[#7C3AED]">{projected.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</p>
      </div>
    </div>
  </section>

  <div class="flex gap-3">
    <Button variant="ghost" on:click={() => history.back()}>Voltar</Button>
    <Button variant="ghost" class="border border-white/15" on:click={() => (window.location.href = '/dashboard')}>
      Dashboard
    </Button>
  </div>
</div>
