<script lang="ts">
  import { Button } from '$lib/components/ui/button';
  import { Input } from '$lib/components/ui/input';
  import { Label } from '$lib/components/ui/label';
  import { Textarea } from '$lib/components/ui/textarea';
  import { activeConstitution, constitutionLoading, constitutionError } from '$lib/store/constitution-store';
  import { authUser } from '$lib/store/auth-store';
  import { getSupabaseClient } from '$lib/services/SupabaseClient';
  import { toastStore } from '$lib/store/toast-store';
  import { onMount } from 'svelte';
  import {
    DEFAULT_CONSTITUTION_HIGH_END,
    CONSTITUTION_STUDIO_SUGGESTIONS,
  } from '$lib/data/arcadia-high-end-defaults';

  let editMode = false;
  let saving = false;
  
  // Form data
  let title = '';
  let narrativeStyle = '';
  let principlesText = '';
  let rulesText = '';

  /** Constitution Studio — pesos (sincronizados com user_finance_profile) */
  let wInvest = 40;
  let wSec = 30;
  let wLife = 30;
  let savingWeights = false;

  // Load constitution data when it becomes available
  $: if ($activeConstitution && !editMode) {
    title = $activeConstitution.title;
    narrativeStyle = $activeConstitution.narrative_style || '';
    principlesText = Array.isArray($activeConstitution.principles) 
      ? $activeConstitution.principles.join('\n') 
      : JSON.stringify($activeConstitution.principles, null, 2);
    rulesText = typeof $activeConstitution.rules === 'object' 
      ? JSON.stringify($activeConstitution.rules, null, 2) 
      : String($activeConstitution.rules || '');
  }

  async function saveConstitution() {
    if (!$authUser || !$activeConstitution) return;
    
    saving = true;
    const supabase = getSupabaseClient();
    if (!supabase) {
      toastStore.error('Erro de conexão com o banco de dados');
      saving = false;
      return;
    }

    try {
      // Parse principles and rules
      let principles: string[] = [];
      let rules: Record<string, any> = {};

      // Try to parse principles as array
      try {
        const parsed = JSON.parse(principlesText);
        if (Array.isArray(parsed)) {
          principles = parsed;
        } else {
          principles = principlesText.split('\n').filter(line => line.trim());
        }
      } catch {
        principles = principlesText.split('\n').filter(line => line.trim());
      }

      // Try to parse rules as object
      try {
        rules = JSON.parse(rulesText);
      } catch {
        // If JSON parsing fails, create a simple object
        rules = { content: rulesText };
      }

      // Update the constitution
      const { error } = await supabase
        .from('constitutions')
        .update({
          title: title.trim(),
          narrative_style: narrativeStyle.trim() || null,
          principles,
          rules,
          version: $activeConstitution.version + 1,
          updated_at: new Date().toISOString()
        })
        .eq('id', $activeConstitution.id);

      if (error) throw error;

      // Update the store
      activeConstitution.update(current => current ? {
        ...current,
        title: title.trim(),
        narrative_style: narrativeStyle.trim() || null,
        principles,
        rules,
        version: current.version + 1
      } : null);

      toastStore.success('Constituição atualizada com sucesso!');
      editMode = false;
    } catch (error) {
      console.error('Erro ao salvar constituição:', error);
      toastStore.error('Erro ao salvar constituição: ' + (error instanceof Error ? error.message : String(error)));
    } finally {
      saving = false;
    }
  }

  onMount(async () => {
    if (!$authUser) return;
    const supabase = getSupabaseClient();
    if (!supabase) return;
    const { data } = await supabase
      .from('user_finance_profile')
      .select('weight_invest, weight_security, weight_life')
      .eq('user_id', $authUser.id)
      .maybeSingle();
    if (data) {
      wInvest = data.weight_invest ?? 40;
      wSec = data.weight_security ?? 30;
      wLife = data.weight_life ?? 30;
    }
  });

  async function applyHighEndTemplate() {
    if (!$authUser || !$activeConstitution) return;
    const supabase = getSupabaseClient();
    if (!supabase) return;
    saving = true;
    try {
      const principles = [...DEFAULT_CONSTITUTION_HIGH_END.principles];
      const rules = { ...DEFAULT_CONSTITUTION_HIGH_END.rules };
      const { error } = await supabase
        .from('constitutions')
        .update({
          title: DEFAULT_CONSTITUTION_HIGH_END.title,
          narrative_style: DEFAULT_CONSTITUTION_HIGH_END.narrative_style,
          principles,
          rules,
          version: $activeConstitution.version + 1,
          updated_at: new Date().toISOString(),
        })
        .eq('id', $activeConstitution.id);
      if (error) throw error;
      activeConstitution.update((c) =>
        c
          ? {
              ...c,
              title: DEFAULT_CONSTITUTION_HIGH_END.title,
              narrative_style: DEFAULT_CONSTITUTION_HIGH_END.narrative_style,
              principles,
              rules,
              version: c.version + 1,
            }
          : null,
      );
      principlesText = principles.join('\n');
      rulesText = JSON.stringify(rules, null, 2);
      title = DEFAULT_CONSTITUTION_HIGH_END.title;
      narrativeStyle = DEFAULT_CONSTITUTION_HIGH_END.narrative_style;
      toastStore.success('Modelo Finanças Pessoais High-End aplicado.');
    } catch (e) {
      toastStore.error(String(e));
    } finally {
      saving = false;
    }
  }

  async function savePriorityWeights() {
    if (!$authUser) return;
    const supabase = getSupabaseClient();
    if (!supabase) return;
    savingWeights = true;
    try {
      const sum = wInvest + wSec + wLife;
      if (sum !== 100) {
        toastStore.error('A soma dos pesos deve ser 100%.');
        return;
      }
      const { error } = await supabase.from('user_finance_profile').upsert({
        user_id: $authUser.id,
        weight_invest: wInvest,
        weight_security: wSec,
        weight_life: wLife,
        updated_at: new Date().toISOString(),
      });
      if (error) throw error;
      toastStore.success('Prioridades guardadas.');
    } catch (e) {
      toastStore.error(String(e));
    } finally {
      savingWeights = false;
    }
  }

  function evolveConstitutionTips() {
    toastStore.info(
      'Sugestões: ' + CONSTITUTION_STUDIO_SUGGESTIONS.slice(0, 2).join(' · '),
    );
  }

  function cancelEdit() {
    editMode = false;
    // Reset form data
    if ($activeConstitution) {
      title = $activeConstitution.title;
      narrativeStyle = $activeConstitution.narrative_style || '';
      principlesText = Array.isArray($activeConstitution.principles) 
        ? $activeConstitution.principles.join('\n') 
        : JSON.stringify($activeConstitution.principles, null, 2);
      rulesText = typeof $activeConstitution.rules === 'object' 
        ? JSON.stringify($activeConstitution.rules, null, 2) 
        : String($activeConstitution.rules || '');
    }
  }
</script>

<svelte:head>
  <title>Constituição - ARCÁDIA OS</title>
</svelte:head>

<div class="min-h-screen bg-gradient-to-br from-primary-500/10 via-tertiary-500/10 to-secondary-500/10 p-4">
  <div class="max-w-4xl mx-auto">
    <!-- Header -->
    <div class="mb-8">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-3xl font-bold text-primary-500 mb-2">Constituição Financeira</h1>
          <p class="text-surface-600">Defina os princípios e regras que guiam suas decisões financeiras</p>
        </div>
        <div class="flex gap-2">
          <Button variant="ghost" on:click={() => window.history.back()}>
            ← Voltar
          </Button>
          {#if !editMode}
            <Button on:click={() => editMode = true} disabled={$constitutionLoading}>
              ✏️ Editar
            </Button>
          {/if}
        </div>
      </div>
    </div>

    {#if $constitutionLoading}
      <div class="flex items-center justify-center py-12">
        <div class="text-center">
          <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-500 mx-auto mb-4"></div>
          <p class="text-surface-600">Carregando constituição...</p>
        </div>
      </div>
    {:else if $constitutionError}
      <div class="bg-red-500/10 border border-red-500/20 rounded-lg p-6 text-center">
        <p class="text-red-400 mb-4">Erro ao carregar constituição:</p>
        <p class="text-red-300 text-sm">{$constitutionError}</p>
      </div>
    {:else if !$activeConstitution}
      <div class="bg-yellow-500/10 border border-yellow-500/20 rounded-lg p-6 text-center">
        <p class="text-yellow-400">Nenhuma constituição encontrada. Aguarde a criação automática...</p>
      </div>
    {:else}
      <div class="space-y-6">
        <!-- Constitution Info -->
        <div class="bg-surface-50 dark:bg-surface-800 rounded-lg p-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-xl font-semibold">Informações Básicas</h2>
            {#if !editMode}
              <span class="text-sm text-surface-500">Versão {$activeConstitution.version}</span>
            {/if}
          </div>
          
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <Label for="title">Título da Constituição</Label>
              {#if editMode}
                <Input 
                  id="title" 
                  bind:value={title} 
                  placeholder="Ex: Minha Constituição Financeira"
                  class="mt-1"
                />
              {:else}
                <p class="mt-1 p-2 bg-surface-100 dark:bg-surface-700 rounded border">{$activeConstitution.title}</p>
              {/if}
            </div>
            
            <div>
              <Label for="narrative-style">Estilo Narrativo</Label>
              {#if editMode}
                <Input 
                  id="narrative-style" 
                  bind:value={narrativeStyle} 
                  placeholder="Ex: conservador, agressivo, equilibrado"
                  class="mt-1"
                />
              {:else}
                <p class="mt-1 p-2 bg-surface-100 dark:bg-surface-700 rounded border">{$activeConstitution.narrative_style || 'Não definido'}</p>
              {/if}
            </div>
          </div>
        </div>

        <!-- Principles -->
        <div class="bg-surface-50 dark:bg-surface-800 rounded-lg p-6">
          <h2 class="text-xl font-semibold mb-4">Princípios Fundamentais</h2>
          <p class="text-sm text-surface-600 mb-4">
            {editMode ? 'Digite um princípio por linha ou use formato JSON:' : 'Seus princípios financeiros fundamentais:'}
          </p>
          
          {#if editMode}
            <Textarea 
              bind:value={principlesText}
              placeholder="Ex:&#10;Sempre priorizar emergências&#10;Investir pelo menos 20% da renda&#10;Evitar dívidas de consumo"
              class="min-h-[150px] font-mono text-sm"
            />
          {:else}
            <div class="bg-surface-100 dark:bg-surface-700 rounded border p-4">
              {#if Array.isArray($activeConstitution.principles)}
                <ul class="space-y-2">
                  {#each $activeConstitution.principles as principle}
                    <li class="flex items-start gap-2">
                      <span class="text-primary-500 mt-1">•</span>
                      <span>{principle}</span>
                    </li>
                  {/each}
                </ul>
              {:else}
                <pre class="text-sm whitespace-pre-wrap">{JSON.stringify($activeConstitution.principles, null, 2)}</pre>
              {/if}
            </div>
          {/if}
        </div>

        <!-- Rules -->
        <div class="bg-surface-50 dark:bg-surface-800 rounded-lg p-6">
          <h2 class="text-xl font-semibold mb-4">Regras e Diretrizes</h2>
          <p class="text-sm text-surface-600 mb-4">
            {editMode ? 'Use formato JSON para regras estruturadas:' : 'Suas regras financeiras específicas:'}
          </p>
          
          {#if editMode}
            <Textarea 
              bind:value={rulesText}
              placeholder="JSON com regras, ex.: emergency_fund, investment_allocation, spending_limits..."
              class="min-h-[200px] font-mono text-sm"
            />
          {:else}
            <div class="bg-surface-100 dark:bg-surface-700 rounded border p-4">
              <pre class="text-sm whitespace-pre-wrap overflow-x-auto">{JSON.stringify($activeConstitution.rules, null, 2)}</pre>
            </div>
          {/if}
        </div>

        <!-- Actions -->
        {#if editMode}
          <div class="flex justify-end gap-3">
            <Button variant="ghost" on:click={cancelEdit} disabled={saving}>
              Cancelar
            </Button>
            <Button on:click={saveConstitution} disabled={saving}>
              {saving ? 'Salvando...' : 'Salvar Constituição'}
            </Button>
          </div>
        {/if}

        <!-- Constitution Studio High-End -->
        <div class="rounded-2xl border border-[#00D4A5]/25 bg-[#0c1018] p-6 space-y-6">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 class="text-xl font-semibold text-[#00D4A5]">Constitution Studio · Finanças Pessoais</h2>
              <p class="text-sm text-slate-400 mt-1">Árvore de valores: raiz = liberdade financeira</p>
            </div>
            <div class="flex flex-wrap gap-2">
              <Button
                variant="ghost"
                class="border border-[#7C3AED]/40 text-[#c4b5fd]"
                on:click={applyHighEndTemplate}
                disabled={saving}
              >
                Aplicar modelo High-End
              </Button>
              <Button variant="ghost" class="border border-white/15" on:click={evolveConstitutionTips}>
                Evoluir constituição (sugestões)
              </Button>
            </div>
          </div>

          <div class="rounded-lg border border-white/10 p-4 bg-black/20">
            <p class="text-xs text-slate-500 mb-2">Visualização em árvore</p>
            <ul class="text-sm text-slate-200 space-y-1 font-mono">
              <li>└─ Liberdade financeira</li>
              <li class="pl-4">├─ Segurança (reserva + seguros)</li>
              <li class="pl-4">├─ Disciplina orçamental (50/30/20)</li>
              <li class="pl-4">└─ Crescimento patrimonial (investimentos)</li>
            </ul>
          </div>

          <div class="space-y-4">
            <p class="text-sm font-medium text-slate-300">Pesos de prioridade (total 100%)</p>
            <div class="grid md:grid-cols-3 gap-4">
              <div>
                <Label for="wi">Investimento ({wInvest}%)</Label>
                <input
                  id="wi"
                  type="range"
                  min="0"
                  max="100"
                  bind:value={wInvest}
                  class="w-full accent-[#00D4A5]"
                />
              </div>
              <div>
                <Label for="ws">Segurança ({wSec}%)</Label>
                <input
                  id="ws"
                  type="range"
                  min="0"
                  max="100"
                  bind:value={wSec}
                  class="w-full accent-[#7C3AED]"
                />
              </div>
              <div>
                <Label for="wl">Qualidade de vida ({wLife}%)</Label>
                <input
                  id="wl"
                  type="range"
                  min="0"
                  max="100"
                  bind:value={wLife}
                  class="w-full accent-emerald-400"
                />
              </div>
            </div>
            <p class="text-xs text-slate-500">Soma: {wInvest + wSec + wLife}%</p>
            <Button size="sm" on:click={savePriorityWeights} disabled={savingWeights}>
              {savingWeights ? 'A guardar…' : 'Guardar prioridades'}
            </Button>
          </div>

          <div>
            <p class="text-xs text-slate-500 mb-2">Sugestões automáticas (clique para copiar ao editar)</p>
            <div class="flex flex-wrap gap-2">
              {#each CONSTITUTION_STUDIO_SUGGESTIONS as s}
                <button
                  type="button"
                  class="text-xs px-3 py-1 rounded-full border border-white/10 bg-white/5 hover:bg-white/10"
                  on:click={() => {
                    navigator.clipboard?.writeText(s);
                    toastStore.info('Copiado.');
                  }}
                >
                  {s.slice(0, 48)}…
                </button>
              {/each}
            </div>
          </div>
        </div>

        <!-- Constitution Preview -->
        {#if !editMode && $activeConstitution}
          <div class="bg-primary-500/5 border border-primary-500/20 rounded-lg p-6">
            <h3 class="text-lg font-semibold mb-3 text-primary-400">Preview do Prompt da Constituição</h3>
            <div class="bg-surface-900 rounded border p-4 text-sm font-mono">
              <pre class="whitespace-pre-wrap text-surface-300">[ARCÁDIA CONSTITUTION]
Título: {$activeConstitution.title} (v{$activeConstitution.version})
Estilo narrativo: {$activeConstitution.narrative_style || 'padrão'}
Princípios:
{JSON.stringify($activeConstitution.principles, null, 2)}
Regras:
{JSON.stringify($activeConstitution.rules, null, 2)}
Respeite esta constituição em toda decisão e recomendação.</pre>
            </div>
          </div>
        {/if}
      </div>
    {/if}
  </div>
</div>