<script lang="ts">
  import ChatInput from "./ChatInput.svelte";
  import ChatMessages from "./ChatMessages.svelte";
  import ModelConfig from "./ModelConfig.svelte";
  import DropdownGroup from "./DropdownGroup.svelte";
  import NoteDrawer from "$lib/components/ui/noteDrawer/NoteDrawer.svelte";
  import { Button } from "$lib/components/ui/button";
  import { Input } from "$lib/components/ui/input";
  import { Label } from "$lib/components/ui/label";
  import { Checkbox } from "$lib/components/ui/checkbox";
  import Tooltip from "$lib/components/ui/tooltip/Tooltip.svelte";
  import { Textarea } from "$lib/components/ui/textarea";
  import { messageStore, sendMessage, streamingStore } from "$lib/store/chat-store";
  import { obsidianSettings } from "$lib/store/obsidian-store";
  import { featureFlags } from "$lib/config/features";
  import { getDrawerStore } from '@skeletonlabs/skeleton';
  import { patterns, patternAPI, systemPrompt, selectedPatternName } from "$lib/store/pattern-store";
  import { availableModels, modelConfig, selectedVendor } from "$lib/store/model-store";
  import { selectedStrategy } from "$lib/store/strategy-store";
  import { summary as autopilotSummary } from "$lib/store/autopilot-metrics-store";
  import { WORKFLOWS, type Workflow } from "$lib/config/workflows";
  import AuthPanel from "$lib/components/chat/AuthPanel.svelte";
  import { authInitialized, authLoading, authUser, initAuth, signOut } from "$lib/store/auth-store";
  import { activeConstitution } from "$lib/store/constitution-store";
  import { ensureUserProfile, loadActiveConstitution } from "$lib/services/ConstitutionService";
  import { onMount } from "svelte";

  let activeWorkflowId: string | null = null;
  let sysInstructionsCollapsed = true;
  let backendOnline: boolean | null = null;
  let lastLoadedConstitutionForUser: string | null = null;

  function applyWorkflow(workflow: Workflow) {
    activeWorkflowId = workflow.id;
    if ($patterns.some((p) => p.Name === workflow.patternName)) {
      patternAPI.selectPattern(workflow.patternName);
    }
    selectedStrategy.set(workflow.strategyName);
  }

  function clearHistory() {
    messageStore.set([]);
    activeWorkflowId = null;
  }

  async function checkBackend() {
    try {
      const res = await fetch('/api/health', { signal: AbortSignal.timeout(2000) });
      if (!res.ok) {
        backendOnline = false;
        return;
      }
      const data = (await res.json()) as { ok?: boolean; geminiConfigured?: boolean };
      backendOnline = data.ok === true && data.geminiConfigured === true;
    } catch {
      backendOnline = false;
    }
  }

  const drawerStore = getDrawerStore();
  function openDrawer() {
    drawerStore.open({});
  }

  // Column width state (percentage values)
  let leftColumnWidth = 50;
  let rightColumnWidth = 50;
  let isDragging = false;
  
  // Message input height state (percentage values)
  const DEFAULT_INPUT_HEIGHT = 30; // Default percentage of the left column
  const MAX_INPUT_HEIGHT = DEFAULT_INPUT_HEIGHT * 2; // Maximum 200% of default height
  const MIN_SYSTEM_INSTRUCTIONS_HEIGHT = 20; // Minimum percentage for system instructions
  let messageInputHeight = DEFAULT_INPUT_HEIGHT;
  let systemInstructionsHeight = 100 - DEFAULT_INPUT_HEIGHT;
  let isVerticalDragging = false;
  let initialMouseY = 0; // Track initial mouse position
  let initialInputHeight = 0; // Track initial input height
  let quickRunning = false;
  let defaultsApplied = false;
  
  // Handle horizontal resize functionality
  function startResize(e: MouseEvent | KeyboardEvent) {
    isDragging = true;
    e.preventDefault();
    
    // Add event listeners for drag and release
    window.addEventListener('mousemove', handleResize);
    window.addEventListener('mouseup', stopResize);
  }
  
  // Handle keyboard events for accessibility
  function handleKeyDown(e: KeyboardEvent) {
    // Only respond to Enter or Space key
    if (e.key === 'Enter' || e.key === ' ') {
      startResize(e);
    }
  }
  
  function handleResize(e: MouseEvent) {
    if (!isDragging) return;
    
    // Get container dimensions
    const container = document.querySelector('.chat-container');
    if (!container) return;
    
    const containerRect = container.getBoundingClientRect();
    const containerWidth = containerRect.width;
    
    // Calculate percentage based on mouse position
    const percentage = ((e.clientX - containerRect.left) / containerWidth) * 100;
    
    // Apply constraints (left: 40-80%, right: 20-60%)
    leftColumnWidth = Math.min(Math.max(percentage, 40), 80);
    rightColumnWidth = 100 - leftColumnWidth;
  }
  
  // Handle vertical resize functionality
  function startVerticalResize(e: MouseEvent | KeyboardEvent) {
    isVerticalDragging = true;
    e.preventDefault();
    
    // Store initial mouse position and input height
    if (e instanceof MouseEvent) {
      initialMouseY = e.clientY;
      initialInputHeight = messageInputHeight;
    }
    
    // Add event listeners for drag and release
    window.addEventListener('mousemove', handleVerticalResize);
    window.addEventListener('mouseup', stopVerticalResize);
  }
  
  function handleVerticalKeyDown(e: KeyboardEvent) {
    // Only respond to Enter or Space key
    if (e.key === 'Enter' || e.key === ' ') {
      startVerticalResize(e);
    }
  }
  
  function handleVerticalResize(e: MouseEvent) {
    if (!isVerticalDragging) return;
    
    // Get container dimensions
    const leftColumn = document.querySelector('.left-column');
    if (!leftColumn) return;
    
    // Get system instructions element to check its actual height
    const sysInstructions = leftColumn.querySelector('.system-instructions');
    if (!sysInstructions) return;
    
    const columnRect = leftColumn.getBoundingClientRect();
    const columnHeight = columnRect.height;
    
    // Calculate height change based on mouse movement
    const mouseDelta = e.clientY - initialMouseY;
    const deltaPercentage = (mouseDelta / columnHeight) * 100;
    const newHeight = initialInputHeight + deltaPercentage;
    
    // Apply constraints to ensure system instructions remain visible
    const minHeight = DEFAULT_INPUT_HEIGHT * 0.25; // 25% of default
    const maxHeight = Math.min(MAX_INPUT_HEIGHT, 100 - MIN_SYSTEM_INSTRUCTIONS_HEIGHT); // Max 200% of default or ensure system instructions are visible
    
    // Calculate new heights
    const constrainedHeight = Math.min(Math.max(newHeight, minHeight), maxHeight);
    const newSysInstructionsHeight = 100 - constrainedHeight;
    
    // Additional safety check - don't allow resize if it would make system instructions too small
    const sysInstructionsPixelHeight = (columnHeight * newSysInstructionsHeight) / 100;
    if (sysInstructionsPixelHeight < 100) return; // Don't resize if it would be less than 100px
    
    // Apply the new heights
    messageInputHeight = constrainedHeight;
    systemInstructionsHeight = newSysInstructionsHeight;
  }
  
  function stopVerticalResize() {
    isVerticalDragging = false;
    window.removeEventListener('mousemove', handleVerticalResize);
    window.removeEventListener('mouseup', stopVerticalResize);
  }
  
  function stopResize() {
    isDragging = false;
    window.removeEventListener('mousemove', handleResize);
    window.removeEventListener('mouseup', stopResize);
  }

  function applySmartDefaults() {
    if (defaultsApplied) return;
    if ($patterns.length === 0 || $availableModels.length === 0) return;

    const hasCurrentPattern = !!$selectedPatternName && $patterns.some((p) => p.Name === $selectedPatternName);
    if (!hasCurrentPattern) {
      const preferredPattern =
        $patterns.find((p) => p.Name === "summarize")
        ?? $patterns.find((p) => p.Name === "extract_wisdom")
        ?? $patterns[0];
      if (preferredPattern) {
        patternAPI.selectPattern(preferredPattern.Name);
      }
    }

    const hasCurrentModel = !!$modelConfig.model && $availableModels.some((m) => m.name === $modelConfig.model);
    if (!hasCurrentModel) {
      const geminiModels = $availableModels.filter((m) => m.vendor.toLowerCase() === "gemini");
      const preferredGeminiModel =
        geminiModels.find((m) => m.name.includes("gemini-2.5-flash"))
        ?? geminiModels.find((m) => m.name.includes("flash"))
        ?? geminiModels[0];
      const fallbackModel = $availableModels[0];
      const selectedModel = preferredGeminiModel ?? fallbackModel;

      if (selectedModel) {
        selectedVendor.set(selectedModel.vendor);
        modelConfig.update((cfg) => ({ ...cfg, model: selectedModel.name }));
      }
    }

    defaultsApplied = true;
  }

  async function runQuickTest() {
    if (quickRunning || $streamingStore) return;
    quickRunning = true;
    try {
      messageStore.set([]);
      await sendMessage(
        "Explique juros compostos em 3 linhas com um exemplo simples para iniciantes.",
        $systemPrompt
      );
    } catch (error) {
      console.error("Quick test failed:", error);
    } finally {
      quickRunning = false;
    }
  }

  // Clean up event listeners when component is destroyed
  onMount(() => {
    initAuth();
    checkBackend();
    const interval = setInterval(checkBackend, 15000);
    return () => {
      window.removeEventListener('mousemove', handleResize);
      window.removeEventListener('mouseup', stopResize);
      window.removeEventListener('mousemove', handleVerticalResize);
      window.removeEventListener('mouseup', stopVerticalResize);
      clearInterval(interval);
    };
  });

  async function bootstrapConstitutionForUser(userId: string, email?: string | null) {
    if (!userId || lastLoadedConstitutionForUser === userId) return;
    try {
      await ensureUserProfile(userId, email);
      await loadActiveConstitution(userId);
      lastLoadedConstitutionForUser = userId;
    } catch (error) {
      console.warn("Falha ao carregar constituição ativa:", error);
    }
  }

  $: if ($authUser?.id) {
    bootstrapConstitutionForUser($authUser.id, $authUser.email);
  }

  $: showObsidian = $featureFlags.enableObsidianIntegration;
  $: msgCount = $messageStore.filter(m => m.role !== 'system').length;
  $: applySmartDefaults();
</script>

{#if !$authInitialized || $authLoading}
  <div class="chat-container h-full w-full p-4">
    <div class="panel-card panel-card-soft p-4">
      <p class="panel-title">Autenticação</p>
      <p class="panel-meta">Carregando sessão Supabase...</p>
    </div>
  </div>
{:else if !$authUser}
  <AuthPanel />
{:else}
<div class="chat-container h-full w-full p-4">
  <div class="chat-workspace">
  <!-- Left Column -->
  <aside class="left-column flex flex-col gap-3 pr-2" style="width: {leftColumnWidth}%">
    <!-- Dropdowns Group with Model Config -->
    <div class="panel-card panel-card-soft p-3">
      <p class="panel-title">Workspace</p>
      <div class="mt-2 rounded-lg bg-background/20">
        <DropdownGroup />
      </div>
    </div>

    <!-- Message Input -->
    <div class="panel-card rounded-lg overflow-hidden" style="height: {messageInputHeight}%; max-height: {MAX_INPUT_HEIGHT}%">
      <p class="panel-title px-3 pt-2">Prompt</p>
      <ChatInput />
    </div>

    <!-- Vertical Resize Handle -->
    <button 
      class="vertical-resize-handle" 
      on:mousedown={startVerticalResize}
      on:keydown={handleVerticalKeyDown}
      type="button"
      aria-label="Resize message input and system instructions"
    ></button>

    <!-- System Instructions (colapsável) -->
    <div class="system-instructions panel-card panel-card-soft flex-1 min-h-[100px] p-3" class:sys-collapsed={sysInstructionsCollapsed}>
      <button
        class="sys-toggle-row"
        type="button"
        on:click={() => sysInstructionsCollapsed = !sysInstructionsCollapsed}
      >
        <p class="panel-title" style="margin:0">System Instructions</p>
        <span class="sys-toggle-icon">{sysInstructionsCollapsed ? '▸' : '▾'}</span>
      </button>
      {#if !sysInstructionsCollapsed}
        <div class="h-full flex flex-col mt-2">
          <Textarea
            bind:value={$systemPrompt}
            readonly={true}
            placeholder="System instructions will appear here when you select a pattern..."
            class="w-full flex-1 bg-primary-800/30 rounded-lg border-none whitespace-pre-wrap overflow-y-auto resize-none text-sm scrollbar-thin scrollbar-thumb-white/10 scrollbar-track-transparent hover:scrollbar-thumb-white/20"
          />
        </div>
      {/if}
    </div>
  </aside>

  <!-- Resize Handle -->
  <button 
    class="resize-handle" 
    on:mousedown={startResize}
    on:keydown={handleKeyDown}
    type="button"
    aria-label="Resize chat panels"
  ></button>

  <!-- Right Column -->
  <div class="flex flex-col gap-3" style="width: {rightColumnWidth}%">
    <!-- Header with status + controls -->
    <div class="panel-card panel-card-soft flex items-center justify-between px-3 py-2">
      <div class="flex items-center gap-2">
        <div class="console-info">
          <div class="flex items-center gap-2">
            <p class="panel-title" style="margin:0">Chat Console</p>
            <span class="backend-badge" class:backend-online={backendOnline === true} class:backend-offline={backendOnline === false} class:backend-checking={backendOnline === null}>
              {#if backendOnline === true}● Online{:else if backendOnline === false}● Offline{:else}● …{/if}
            </span>
          </div>
          <p class="panel-meta" style="margin-top:0.15rem">
            Pattern: <strong>{$selectedPatternName || "—"}</strong> · Modelo: <strong>{$selectedVendor || "Auto"} / {$modelConfig.model || "default"}</strong>
            {#if msgCount > 0}<span style="margin-left:0.5rem;opacity:0.6">· {msgCount} msg{msgCount > 1 ? 's' : ''}</span>{/if}
          </p>
        </div>
        {#if showObsidian}
          <div class="flex items-center gap-2">
            <div class="flex items-center gap-1">
              <Checkbox bind:checked={$obsidianSettings.saveToObsidian} id="save-to-obsidian" class="h-3 w-3" />
              <Label for="save-to-obsidian" class="text-xs text-white/70">Obsidian</Label>
            </div>
            {#if $obsidianSettings.saveToObsidian}
              <Input id="note-name" bind:value={$obsidianSettings.noteName} placeholder="Note name..." class="h-6 text-xs w-40 bg-white/5 border-none focus:ring-1 ring-white/20" />
            {/if}
          </div>
        {/if}
      </div>
      <div class="flex items-center gap-1">
        {#if $activeConstitution}
          <span class="text-[10px] px-2 py-1 rounded-full border border-emerald-400/30 bg-emerald-500/10 text-emerald-200">
            Constituição: {$activeConstitution.title}
          </span>
        {/if}
        {#if msgCount > 0}
          <Button variant="ghost" size="sm" class="h-6 px-2 text-xs opacity-60 hover:opacity-100 hover:text-red-400" on:click={clearHistory} title="Limpar conversa">
            🗑 Limpar
          </Button>
        {/if}
        <Button variant="ghost" size="sm" class="h-6 px-2 text-xs opacity-70 hover:opacity-100" on:click={signOut}>
          Sair
        </Button>
        <Button variant="ghost" size="sm" class="h-6 px-2 text-xs opacity-70 hover:opacity-100" on:click={openDrawer}>
          <Tooltip text="Take Notes" position="left"><span>📝 Notas</span></Tooltip>
        </Button>
      </div>
    </div>

    <div class="panel-card panel-card-soft quick-guide">
      <p class="panel-title">Atalhos de Workflow</p>
      <div class="workflow-grid">
        {#each WORKFLOWS as wf}
          <button
            class="workflow-chip"
            class:workflow-chip-active={activeWorkflowId === wf.id}
            type="button"
            title={wf.description}
            on:click={() => applyWorkflow(wf)}
          >
            <span class="workflow-icon">{wf.icon}</span>
            <span class="workflow-label">{wf.label}</span>
          </button>
        {/each}
      </div>
      <div class="guide-actions">
        <Button
          variant="ghost"
          size="sm"
          class="h-7 px-3 text-xs"
          on:click={runQuickTest}
          disabled={quickRunning || $streamingStore}
        >
          {quickRunning ? "Executando..." : "Teste rápido"}
        </Button>
      </div>
    </div>

    <div class="panel-card panel-card-soft autopilot-stats">
      <p class="panel-title">Autopilot — Sessão</p>
      {#if $autopilotSummary.total === 0}
        <p class="guide-step" style="color: rgba(148,163,184,0.7)">Nenhuma execução ainda.</p>
      {:else}
        <div class="stats-row">
          <span class="stat-item">✅ {$autopilotSummary.successRate}% sucesso</span>
          <span class="stat-item">🔀 {$autopilotSummary.fallbackRate}% fallback</span>
          <span class="stat-item">✍️ {$autopilotSummary.refinedRate}% refinados</span>
          <span class="stat-item">⏱ {$autopilotSummary.avgDurationMs}ms</span>
          <span class="stat-item">🎯 conf. {$autopilotSummary.avgConfidence}</span>
        </div>
        <p class="panel-meta">{$autopilotSummary.total} execuções registradas nesta sessão.</p>
      {/if}
    </div>

    <!-- Chat Area -->
    <div class="flex-1 min-h-0">
      <!-- Chat History -->
      <div class="panel-card h-full min-h-0 overflow-y-scroll scrollbar-thin scrollbar-thumb-white/10 scrollbar-track-transparent hover:scrollbar-thumb-white/20">
        <ChatMessages />
        <div class="h-32"></div> <!-- Spacer div to ensure scrolling works properly -->
      </div>
    </div>
  </div>
  </div>
</div>

<NoteDrawer />
{/if}

<style>
  .chat-workspace {
    display: flex;
    gap: 0;
    width: 100%;
    height: 100%;
    border-radius: 0.85rem;
    border: 1px solid rgba(255, 255, 255, 0.08);
    background: rgba(2, 6, 23, 0.35);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
    overflow: hidden;
  }

  .panel-card {
    border-radius: 0.75rem;
    border: 1px solid rgba(255, 255, 255, 0.08);
    background: rgba(15, 23, 42, 0.55);
    backdrop-filter: blur(6px);
  }

  .panel-card-soft {
    background: rgba(15, 23, 42, 0.35);
  }

  .panel-title {
    font-size: 0.78rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: rgba(203, 213, 225, 0.9);
    margin: 0;
  }

  .panel-subtitle {
    font-size: 0.74rem;
    color: rgba(148, 163, 184, 0.92);
    margin: 0.1rem 0 0;
  }

  .panel-meta {
    margin: 0.2rem 0 0;
    font-size: 0.72rem;
    color: rgba(148, 163, 184, 0.85);
  }

  .console-info strong {
    color: rgba(226, 232, 240, 0.95);
    font-weight: 600;
  }

  .quick-guide {
    padding: 0.65rem 0.75rem;
  }

  .guide-step {
    margin: 0.15rem 0;
    font-size: 0.78rem;
    color: rgba(203, 213, 225, 0.92);
  }

  .guide-actions {
    margin-top: 0.35rem;
    display: flex;
    justify-content: flex-end;
  }

  .workflow-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 0.35rem;
    margin: 0.4rem 0 0.5rem;
  }

  .workflow-chip {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    padding: 0.2rem 0.55rem;
    border-radius: 999px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    background: rgba(15, 23, 42, 0.45);
    cursor: pointer;
    transition: background 0.15s, border-color 0.15s;
    font-size: 0.72rem;
    color: rgba(203, 213, 225, 0.9);
  }

  .workflow-chip:hover {
    background: rgba(99, 102, 241, 0.2);
    border-color: rgba(99, 102, 241, 0.45);
  }

  .workflow-chip-active {
    background: rgba(99, 102, 241, 0.3);
    border-color: rgba(99, 102, 241, 0.65);
    color: #e0e7ff;
  }

  .workflow-icon {
    font-size: 0.85rem;
  }

  .autopilot-stats {
    padding: 0.55rem 0.75rem;
  }

  .stats-row {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    margin: 0.3rem 0 0.2rem;
  }

  .stat-item {
    font-size: 0.72rem;
    color: rgba(148, 163, 184, 0.9);
    background: rgba(15, 23, 42, 0.4);
    border: 1px solid rgba(255, 255, 255, 0.07);
    border-radius: 0.4rem;
    padding: 0.1rem 0.4rem;
  }

  /* Horizontal resize handle */
  .resize-handle {
    width: 6px;
    margin: 0 -3px;
    height: 100%;
    cursor: col-resize;
    position: relative;
    z-index: 10;
    transition: background-color 0.2s;
  }

  .resize-handle::after {
    content: "";
    position: absolute;
    top: 0;
    left: 50%;
    transform: translateX(-50%);
    height: 100%;
    width: 2px;
    background-color: rgba(255, 255, 255, 0.1);
    transition: background-color 0.2s, width 0.2s;
  }

  .resize-handle:hover::after,
  .resize-handle:focus::after {
    background-color: rgba(255, 255, 255, 0.3);
    width: 4px;
  }

  .resize-handle:focus {
    outline: none;
  }

  .resize-handle:focus-visible::after {
    background-color: rgba(255, 255, 255, 0.5);
    width: 4px;
  }

  /* Vertical resize handle */
  .vertical-resize-handle {
    height: 6px;
    margin: -3px 0;
    width: 100%;
    cursor: row-resize;
    position: relative;
    z-index: 10;
    transition: background-color 0.2s;
  }

  .vertical-resize-handle::after {
    content: "";
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 100%;
    height: 2px;
    background-color: rgba(255, 255, 255, 0.1);
    transition: background-color 0.2s, height 0.2s;
  }

  .vertical-resize-handle:hover::after,
  .vertical-resize-handle:focus::after {
    background-color: rgba(255, 255, 255, 0.3);
    height: 4px;
  }

  .vertical-resize-handle:focus {
    outline: none;
  }

  .vertical-resize-handle:focus-visible::after {
    background-color: rgba(255, 255, 255, 0.5);
    height: 4px;
  }

  @media (max-width: 1024px) {
    .chat-container {
      padding: 0.5rem;
    }

    .left-column {
      min-width: 320px;
    }
  }

  @keyframes flash {
    0% { opacity: 1; }
    50% { opacity: 0.5; }
    100% { opacity: 1; }
  }

  /* Backend status badge */
  .backend-badge {
    font-size: 0.65rem;
    padding: 0.1rem 0.4rem;
    border-radius: 999px;
    border: 1px solid transparent;
    font-weight: 600;
    letter-spacing: 0.03em;
  }
  .backend-online  { color: #4ade80; border-color: rgba(74,222,128,0.3); background: rgba(74,222,128,0.1); }
  .backend-offline { color: #f87171; border-color: rgba(248,113,113,0.3); background: rgba(248,113,113,0.1); }
  .backend-checking { color: rgba(148,163,184,0.7); border-color: rgba(148,163,184,0.2); }

  /* System Instructions toggle */
  .sys-toggle-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    background: none;
    border: none;
    cursor: pointer;
    padding: 0;
  }
  .sys-toggle-icon {
    font-size: 0.75rem;
    color: rgba(148,163,184,0.7);
  }
  .sys-collapsed {
    flex: 0 0 auto !important;
    min-height: auto !important;
  }
</style>
