<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { authUser, authLoading, initAuth } from '$lib/store/auth-store';
  import AuthPanel from '$lib/components/chat/AuthPanel.svelte';

  let isReady = false;

  onMount(async () => {
    await initAuth();
    isReady = true;
  });

  // Se o usuário já está logado, redireciona
  $: if (isReady && !$authLoading && $authUser) {
    const redirectTo = $page.url.searchParams.get('redirect') || '/dashboard';
    goto(redirectTo);
  }
</script>

<svelte:head>
  <title>Login - ARCÁDIA OS</title>
</svelte:head>

{#if $authLoading || !isReady}
  <div class="flex items-center justify-center min-h-screen">
    <div class="text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-500 mx-auto mb-4"></div>
      <p class="text-surface-600">Carregando...</p>
    </div>
  </div>
{:else if !$authUser}
  <div class="flex items-center justify-center min-h-screen bg-gradient-to-br from-primary-500/10 via-tertiary-500/10 to-secondary-500/10">
    <div class="max-w-md w-full mx-4">
      <div class="text-center mb-8">
        <h1 class="text-4xl font-bold text-primary-500 mb-2">ARCÁDIA OS</h1>
        <p class="text-lg text-surface-600 mb-1">Sistema Operacional de Decisão Autônoma</p>
        <p class="text-sm text-surface-500">Máximo ganho financeiro com carga mental mínima</p>
      </div>
      
      <div class="bg-surface-50 dark:bg-surface-800 rounded-lg shadow-lg p-6">
        <h2 class="text-xl font-semibold text-center mb-6">Acesse sua conta</h2>
        <AuthPanel />
      </div>
      
      <div class="text-center mt-6 space-y-2 text-sm text-surface-500">
        <p>Primeira vez? O sistema criará automaticamente sua constituição financeira personalizada.</p>
        <p>
          <a href="/logout" class="text-primary-500 hover:underline">Remover sessão neste computador</a>
        </p>
      </div>
    </div>
  </div>
{/if}