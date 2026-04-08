<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { authUser, authLoading, initAuth } from '$lib/store/auth-store';
  import { loadActiveConstitution } from '$lib/services/ConstitutionService';
  import AuthPanel from '$lib/components/chat/AuthPanel.svelte';
  import LegalDisclaimer from '$lib/components/LegalDisclaimer.svelte';
  import { getSupabaseClient } from '$lib/services/SupabaseClient';
  import type { LayoutData } from './$types';
  import { Home, MessageCircle, FileText, BarChart3 } from 'lucide-svelte';

  export let data: LayoutData;

  let isReady = false;

  onMount(async () => {
    // Inicializa o sistema de autenticação
    await initAuth();
    isReady = true;
  });

  // Reativo: carrega a constituição quando o usuário faz login
  $: if ($authUser && isReady) {
    void loadActiveConstitution($authUser.id);
  }

  // Reativo: redireciona para login se necessário
  $: if (isReady && !$authLoading && data.requiresAuth && !$authUser) {
    goto(`/login?redirect=${encodeURIComponent(data.currentPath)}`);
  }

  // Reativo: redireciona para onboarding se necessário
  $: if (isReady && !$authLoading && $authUser && data.currentPath !== '/onboarding') {
    checkOnboardingStatus();
  }

  async function checkOnboardingStatus() {
    if (!$authUser) return;
    
    const supabase = getSupabaseClient();
    if (!supabase) return;

    try {
      const { data: userData, error } = await supabase
        .from('users')
        .select('onboarding_completed')
        .eq('id', $authUser.id)
        .single();

      if (error) {
        console.error('Erro ao verificar onboarding:', error);
        return;
      }

      // Se onboarding não foi completado, redireciona
      if (!userData?.onboarding_completed) {
        goto('/onboarding');
      }
    } catch (error) {
      console.error('Erro ao verificar status do onboarding:', error);
    }
  }
</script>

{#if $authLoading || !isReady}
  <!-- Loading state -->
  <div class="flex items-center justify-center min-h-screen">
    <div class="text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-500 mx-auto mb-4"></div>
      <p class="text-surface-600">Carregando ARCÁDIA OS...</p>
    </div>
  </div>
{:else if data.requiresAuth && !$authUser}
  <!-- Auth required but user not logged in -->
  <div class="flex min-h-screen flex-col">
    <div class="flex flex-1 items-center justify-center">
      <div class="max-w-md w-full px-4">
        <div class="text-center mb-8">
          <h1 class="text-3xl font-bold text-primary-500 mb-2">ARCÁDIA OS</h1>
          <p class="text-surface-600">Sistema Operacional de Decisão Autônoma</p>
        </div>
        <AuthPanel />
      </div>
    </div>
    <LegalDisclaimer />
  </div>
{:else}
  <!-- User is authenticated or route doesn't require auth -->
  <div class="min-h-screen flex flex-col">
    <div class="flex-1 pb-20 md:pb-0">
      <slot />
    </div>
    <LegalDisclaimer />
    {#if $authUser && data.requiresAuth}
      <nav
        class="md:hidden fixed bottom-0 inset-x-0 z-30 flex justify-around border-t border-white/10 bg-[#0b0f14]/95 backdrop-blur py-2 safe-area-pb"
        aria-label="Navegação principal"
      >
        <a
          href="/dashboard"
          class="flex flex-col items-center gap-0.5 text-[10px] {$page.url.pathname.startsWith('/dashboard') ? 'text-[#00D4A5]' : 'text-slate-500'}"
        >
          <Home class="h-5 w-5" />
          Início
        </a>
        <a
          href="/chat"
          class="flex flex-col items-center gap-0.5 text-[10px] {$page.url.pathname.startsWith('/chat') ? 'text-[#00D4A5]' : 'text-slate-500'}"
        >
          <MessageCircle class="h-5 w-5" />
          CFO
        </a>
        <a
          href="/constitution"
          class="flex flex-col items-center gap-0.5 text-[10px] {$page.url.pathname.startsWith('/constitution') ? 'text-[#00D4A5]' : 'text-slate-500'}"
        >
          <FileText class="h-5 w-5" />
          Constituição
        </a>
        <a
          href="/reports"
          class="flex flex-col items-center gap-0.5 text-[10px] {$page.url.pathname.startsWith('/reports') ? 'text-[#00D4A5]' : 'text-slate-500'}"
        >
          <BarChart3 class="h-5 w-5" />
          Relatórios
        </a>
      </nav>
    {/if}
  </div>
{/if}