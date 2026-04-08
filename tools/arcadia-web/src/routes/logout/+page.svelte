<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { signOut } from '$lib/store/auth-store';

  let status = 'A terminar sessão neste computador…';

  onMount(async () => {
    try {
      await signOut();
      status = 'Sessão removida.';
      await goto('/login');
    } catch (e) {
      status = 'Não foi possível terminar a sessão. Limpe os dados do site para este endereço no navegador.';
      console.error(e);
    }
  });
</script>

<svelte:head>
  <title>Sair - ARCÁDIA OS</title>
</svelte:head>

<div class="flex min-h-screen flex-col items-center justify-center gap-2 px-4 text-center text-surface-600">
  <p class="text-sm">{status}</p>
</div>
