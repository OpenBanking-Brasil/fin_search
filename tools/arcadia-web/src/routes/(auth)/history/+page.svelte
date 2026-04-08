<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { Button } from '$lib/components/ui/button';
  import { Input } from '$lib/components/ui/input';
  import { authUser } from '$lib/store/auth-store';
  import { getSupabaseClient } from '$lib/services/SupabaseClient';

  // Filters and pagination
  let searchTerm = '';
  let statusFilter = 'all'; // all, executed, pending, rejected
  let typeFilter = 'all'; // all, investment, purchase, sale, etc.
  let currentPage = 1;
  let itemsPerPage = 20;

  // Data
  let decisions: any[] = [];
  let sessions: any[] = [];
  let loading = true;
  let error: string | null = null;
  let totalCount = 0;

  // Selected decision for details
  let selectedDecision: any = null;
  let showDetails = false;

  async function loadDecisions() {
    if (!$authUser) return;
    
    const supabase = getSupabaseClient();
    if (!supabase) {
      error = 'Erro de conexão com o banco de dados';
      loading = false;
      return;
    }

    try {
      loading = true;
      error = null;

      // Build query with filters
      let query = supabase
        .from('decision_history')
        .select(`
          *,
          sessions!inner(title, created_at)
        `, { count: 'exact' })
        .eq('user_id', $authUser.id)
        .order('executed_at', { ascending: false });

      // Apply filters
      if (statusFilter !== 'all') {
        query = query.eq('status', statusFilter);
      }
      
      if (typeFilter !== 'all') {
        query = query.eq('decision_type', typeFilter);
      }

      if (searchTerm.trim()) {
        query = query.or(`description.ilike.%${searchTerm}%,sessions.title.ilike.%${searchTerm}%`);
      }

      // Apply pagination
      const from = (currentPage - 1) * itemsPerPage;
      const to = from + itemsPerPage - 1;
      query = query.range(from, to);

      const { data, error: queryError, count } = await query;
      
      if (queryError) throw queryError;
      
      decisions = data || [];
      totalCount = count || 0;

    } catch (err) {
      console.error('Erro ao carregar histórico:', err);
      error = err instanceof Error ? err.message : String(err);
    } finally {
      loading = false;
    }
  }

  async function loadSessions() {
    if (!$authUser) return;
    
    const supabase = getSupabaseClient();
    if (!supabase) return;

    try {
      const { data, error: sessionsError } = await supabase
        .from('sessions')
        .select('id, title, created_at')
        .eq('user_id', $authUser.id)
        .order('created_at', { ascending: false })
        .limit(50);
      
      if (sessionsError) throw sessionsError;
      sessions = data || [];
    } catch (err) {
      console.error('Erro ao carregar sessões:', err);
    }
  }

  function openDetails(decision: any) {
    selectedDecision = decision;
    showDetails = true;
  }

  function closeDetails() {
    selectedDecision = null;
    showDetails = false;
  }

  // Format functions
  function formatCurrency(value: number | null): string {
    if (value === null || value === undefined) return 'N/A';
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(value);
  }

  function formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString('pt-BR');
  }

  function formatPercentage(value: number | null): string {
    if (value === null || value === undefined) return 'N/A';
    return `${value.toFixed(1)}%`;
  }

  function getStatusColor(status: string): string {
    switch (status) {
      case 'executed': return 'text-green-400 bg-green-500/20';
      case 'pending': return 'text-yellow-400 bg-yellow-500/20';
      case 'rejected': return 'text-red-400 bg-red-500/20';
      default: return 'text-surface-400 bg-surface-500/20';
    }
  }

  function getStatusLabel(status: string): string {
    switch (status) {
      case 'executed': return 'Executada';
      case 'pending': return 'Pendente';
      case 'rejected': return 'Rejeitada';
      default: return 'Desconhecido';
    }
  }

  function getTypeLabel(type: string): string {
    const typeLabels: Record<string, string> = {
      'investment': 'Investimento',
      'purchase': 'Compra',
      'sale': 'Venda',
      'financing': 'Financiamento',
      'savings': 'Poupança',
      'insurance': 'Seguro',
      'budgeting': 'Orçamento',
      'general': 'Geral'
    };
    return typeLabels[type] || type;
  }

  // Reactive statements
  $: totalPages = Math.ceil(totalCount / itemsPerPage);
  $: hasNextPage = currentPage < totalPages;
  $: hasPrevPage = currentPage > 1;

  // Watch for filter changes
  $: if (searchTerm !== undefined || statusFilter !== undefined || typeFilter !== undefined) {
    currentPage = 1; // Reset to first page when filters change
    loadDecisions();
  }

  onMount(() => {
    loadDecisions();
    loadSessions();
  });

  function nextPage() {
    if (hasNextPage) {
      currentPage++;
      loadDecisions();
    }
  }

  function prevPage() {
    if (hasPrevPage) {
      currentPage--;
      loadDecisions();
    }
  }

  function goToPage(page: number) {
    if (page >= 1 && page <= totalPages) {
      currentPage = page;
      loadDecisions();
    }
  }
</script>

<svelte:head>
  <title>Histórico de Decisões - ARCÁDIA OS</title>
</svelte:head>

<div class="min-h-screen bg-gradient-to-br from-primary-500/10 via-tertiary-500/10 to-secondary-500/10 p-4">
  <div class="max-w-7xl mx-auto">
    <!-- Header -->
    <div class="mb-8">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-3xl font-bold text-primary-500 mb-2">Histórico de Decisões</h1>
          <p class="text-surface-600">Acompanhe todas as decisões tomadas pelo Parlamento Cognitivo</p>
        </div>
        <div class="flex gap-2">
          <Button variant="ghost" on:click={() => window.location.href = '/dashboard'}>
            📊 Dashboard
          </Button>
          <Button variant="ghost" on:click={() => window.location.href = '/chat'}>
            💬 Chat
          </Button>
          <Button on:click={loadDecisions} disabled={loading}>
            {loading ? '⟳' : '🔄'} Atualizar
          </Button>
        </div>
      </div>
    </div>

    <!-- Filters -->
    <div class="bg-surface-50 dark:bg-surface-800 rounded-lg p-6 mb-6">
      <h2 class="text-lg font-semibold mb-4">Filtros</h2>
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div>
          <label class="block text-sm font-medium mb-2">Buscar</label>
          <Input 
            bind:value={searchTerm} 
            placeholder="Buscar por descrição ou sessão..."
            class="w-full"
          />
        </div>
        <div>
          <label class="block text-sm font-medium mb-2">Status</label>
          <select bind:value={statusFilter} class="w-full p-2 border rounded-lg bg-surface-100 dark:bg-surface-700">
            <option value="all">Todos</option>
            <option value="executed">Executadas</option>
            <option value="pending">Pendentes</option>
            <option value="rejected">Rejeitadas</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium mb-2">Tipo</label>
          <select bind:value={typeFilter} class="w-full p-2 border rounded-lg bg-surface-100 dark:bg-surface-700">
            <option value="all">Todos</option>
            <option value="investment">Investimento</option>
            <option value="purchase">Compra</option>
            <option value="sale">Venda</option>
            <option value="financing">Financiamento</option>
            <option value="savings">Poupança</option>
            <option value="insurance">Seguro</option>
            <option value="budgeting">Orçamento</option>
            <option value="general">Geral</option>
          </select>
        </div>
        <div class="flex items-end">
          <Button variant="ghost" on:click={() => {
            searchTerm = '';
            statusFilter = 'all';
            typeFilter = 'all';
            currentPage = 1;
          }}>
            Limpar Filtros
          </Button>
        </div>
      </div>
    </div>

    {#if loading}
      <div class="flex items-center justify-center py-12">
        <div class="text-center">
          <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-500 mx-auto mb-4"></div>
          <p class="text-surface-600">Carregando histórico...</p>
        </div>
      </div>
    {:else if error}
      <div class="bg-red-500/10 border border-red-500/20 rounded-lg p-6 text-center">
        <p class="text-red-400 mb-4">Erro ao carregar histórico:</p>
        <p class="text-red-300 text-sm">{error}</p>
        <Button class="mt-4" on:click={loadDecisions}>Tentar novamente</Button>
      </div>
    {:else if decisions.length === 0}
      <div class="bg-surface-50 dark:bg-surface-800 rounded-lg p-8 text-center">
        <p class="text-surface-500 mb-4">Nenhuma decisão encontrada</p>
        <p class="text-sm text-surface-400 mb-6">
          {searchTerm || statusFilter !== 'all' || typeFilter !== 'all' 
            ? 'Tente ajustar os filtros ou fazer uma nova consulta no chat.'
            : 'Comece fazendo perguntas financeiras no chat para ver decisões aqui.'}
        </p>
        <Button on:click={() => window.location.href = '/chat'}>
          Ir para o Chat →
        </Button>
      </div>
    {:else}
      <div class="space-y-4">
        <!-- Results info -->
        <div class="flex items-center justify-between text-sm text-surface-600">
          <p>
            Mostrando {(currentPage - 1) * itemsPerPage + 1} - {Math.min(currentPage * itemsPerPage, totalCount)} de {totalCount} decisões
          </p>
          <p>Página {currentPage} de {totalPages}</p>
        </div>

        <!-- Decisions list -->
        <div class="space-y-3">
          {#each decisions as decision}
            <div class="bg-surface-50 dark:bg-surface-800 rounded-lg p-4 border border-surface-200 dark:border-surface-700 hover:border-primary-500/30 transition-colors cursor-pointer"
                 on:click={() => openDetails(decision)}>
              <div class="flex items-start justify-between">
                <div class="flex-1">
                  <div class="flex items-center gap-3 mb-2">
                    <span class="text-sm font-medium text-primary-400">
                      {getTypeLabel(decision.decision_type)}
                    </span>
                    <span class="text-xs px-2 py-1 rounded-full {getStatusColor(decision.status)}">
                      {getStatusLabel(decision.status)}
                    </span>
                    {#if decision.confidence_score}
                      <span class="text-xs text-surface-500">
                        {formatPercentage(decision.confidence_score)} confiança
                      </span>
                    {/if}
                  </div>
                  <p class="text-sm text-surface-600 mb-2 line-clamp-2">
                    {decision.description || 'Sem descrição'}
                  </p>
                  <div class="flex items-center gap-4 text-xs text-surface-500">
                    <span>Sessão: {decision.sessions?.title || 'N/A'}</span>
                    <span>{formatDate(decision.executed_at)}</span>
                    {#if decision.financial_impact}
                      <span class="font-medium {decision.financial_impact >= 0 ? 'text-green-400' : 'text-red-400'}">
                        {formatCurrency(decision.financial_impact)}
                      </span>
                    {/if}
                  </div>
                </div>
                <div class="text-right">
                  <Button variant="ghost" size="sm">
                    Ver detalhes →
                  </Button>
                </div>
              </div>
            </div>
          {/each}
        </div>

        <!-- Pagination -->
        {#if totalPages > 1}
          <div class="flex items-center justify-center gap-2 mt-8">
            <Button variant="ghost" size="sm" disabled={!hasPrevPage} on:click={prevPage}>
              ← Anterior
            </Button>
            
            {#each Array.from({length: Math.min(5, totalPages)}, (_, i) => {
              const page = Math.max(1, Math.min(totalPages - 4, currentPage - 2)) + i;
              return page;
            }) as page}
              <Button 
                variant={page === currentPage ? 'default' : 'ghost'} 
                size="sm" 
                on:click={() => goToPage(page)}
              >
                {page}
              </Button>
            {/each}
            
            <Button variant="ghost" size="sm" disabled={!hasNextPage} on:click={nextPage}>
              Próxima →
            </Button>
          </div>
        {/if}
      </div>
    {/if}
  </div>
</div>

<!-- Decision Details Modal -->
{#if showDetails && selectedDecision}
  <div class="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50" on:click={closeDetails}>
    <div class="bg-surface-50 dark:bg-surface-800 rounded-lg p-6 max-w-4xl w-full max-h-[90vh] overflow-y-auto" on:click|stopPropagation>
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-semibold">Detalhes da Decisão</h2>
        <Button variant="ghost" size="sm" on:click={closeDetails}>✕</Button>
      </div>
      
      <div class="space-y-6">
        <!-- Basic Info -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <h3 class="font-medium mb-2">Informações Básicas</h3>
            <div class="space-y-2 text-sm">
              <p><strong>Tipo:</strong> {getTypeLabel(selectedDecision.decision_type)}</p>
              <p><strong>Status:</strong> <span class="px-2 py-1 rounded {getStatusColor(selectedDecision.status)}">{getStatusLabel(selectedDecision.status)}</span></p>
              <p><strong>Data:</strong> {formatDate(selectedDecision.executed_at)}</p>
              <p><strong>Sessão:</strong> {selectedDecision.sessions?.title || 'N/A'}</p>
            </div>
          </div>
          <div>
            <h3 class="font-medium mb-2">Métricas</h3>
            <div class="space-y-2 text-sm">
              <p><strong>Confiança:</strong> {formatPercentage(selectedDecision.confidence_score)}</p>
              {#if selectedDecision.financial_impact}
                <p><strong>Impacto Financeiro:</strong> 
                  <span class="{selectedDecision.financial_impact >= 0 ? 'text-green-400' : 'text-red-400'}">
                    {formatCurrency(selectedDecision.financial_impact)}
                  </span>
                </p>
              {/if}
            </div>
          </div>
        </div>

        <!-- Description -->
        <div>
          <h3 class="font-medium mb-2">Descrição</h3>
          <div class="bg-surface-100 dark:bg-surface-700 rounded p-4 text-sm">
            {selectedDecision.description || 'Sem descrição disponível'}
          </div>
        </div>

        <!-- Risks -->
        {#if selectedDecision.risks && selectedDecision.risks.length > 0}
          <div>
            <h3 class="font-medium mb-2">Riscos Identificados</h3>
            <div class="bg-red-500/10 border border-red-500/20 rounded p-4">
              <ul class="space-y-1 text-sm">
                {#each selectedDecision.risks as risk}
                  <li class="flex items-start gap-2">
                    <span class="text-red-400 mt-1">•</span>
                    <span>{risk}</span>
                  </li>
                {/each}
              </ul>
            </div>
          </div>
        {/if}

        <!-- Execution Plan -->
        {#if selectedDecision.execution_plan && selectedDecision.execution_plan.length > 0}
          <div>
            <h3 class="font-medium mb-2">Plano de Execução</h3>
            <div class="bg-blue-500/10 border border-blue-500/20 rounded p-4">
              <ol class="space-y-2 text-sm">
                {#each selectedDecision.execution_plan as step, index}
                  <li class="flex items-start gap-2">
                    <span class="text-blue-400 font-medium">{index + 1}.</span>
                    <span>{step}</span>
                  </li>
                {/each}
              </ol>
            </div>
          </div>
        {/if}

        <!-- Metadata -->
        {#if selectedDecision.metadata}
          <div>
            <h3 class="font-medium mb-2">Metadados</h3>
            <div class="bg-surface-100 dark:bg-surface-700 rounded p-4">
              <pre class="text-xs whitespace-pre-wrap overflow-x-auto">{JSON.stringify(selectedDecision.metadata, null, 2)}</pre>
            </div>
          </div>
        {/if}
      </div>
    </div>
  </div>
{/if}

<style>
  .line-clamp-2 {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
</style>