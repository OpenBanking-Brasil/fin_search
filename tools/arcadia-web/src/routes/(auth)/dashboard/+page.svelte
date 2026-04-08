<script lang="ts">
  import ArcadiaDashboard from '$lib/components/arcadia/ArcadiaDashboard.svelte';
  import { onMount } from 'svelte';
  import { theme } from '$lib/store/theme-store';

  onMount(() => {
    document.documentElement.classList.add('dark');
    theme.setTheme('my-custom-theme');
  });
</script>

<svelte:head>
  <title>Arcádia Dashboard — ARCÁDIA OS</title>
  <meta name="description" content="ARCÁDIA OS Finanças Pessoais High-End — saldo, orçamento, reserva, fluxo e score." />
</svelte:head>

<ArcadiaDashboard />
