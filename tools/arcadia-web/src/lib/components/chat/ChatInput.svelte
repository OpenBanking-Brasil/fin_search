<script lang="ts">
  import { Button } from "$lib/components/ui/button";
  import { Textarea } from "$lib/components/ui/textarea";
  import { sendMessage, messageStore, currentSession } from '$lib/store/chat-store';
  import { systemPrompt, selectedPatternName, patterns, patternAPI } from '$lib/store/pattern-store';
  import { getToastStore } from '@skeletonlabs/skeleton';
  import { FileButton } from '@skeletonlabs/skeleton';
  import { Paperclip, Send } from 'lucide-svelte';
  import { onMount } from 'svelte';
  import { get } from 'svelte/store';
  import { getTranscript } from '$lib/services/transcriptService';
  import { ChatService } from '$lib/services/ChatService';
  import { languageStore } from '$lib/store/language-store';
  import { selectedStrategy } from '$lib/store/strategy-store';
  import { obsidianSettings, updateObsidianSettings } from '$lib/store/obsidian-store';
  import { PdfConversionService } from '$lib/services/PdfConversionService';
  import { route } from '$lib/services/IntentRouterService';
  import { recordEvent } from '$lib/store/autopilot-metrics-store';
  import { authUser } from '$lib/store/auth-store';
  import { activeConstitution } from '$lib/store/constitution-store';
  import { getConstitutionPromptFromStore } from '$lib/services/ConstitutionService';
  import { ParliamentPersistenceService } from '$lib/services/ParliamentPersistenceService';
  import Orchestrator from '$lib/services/Orchestrator';

  const pdfService = new PdfConversionService();
  const chatService = new ChatService();
  const parliamentPersistence = new ParliamentPersistenceService();
  const orchestrator = new Orchestrator();

  let userInput = "";
  const autoInterpreterEnabled = true;
  let isYouTubeURL = false;
  const toastStore = getToastStore();
  let files: FileList | undefined = undefined;
  let uploadedFiles: string[] = [];
  let fileContents: string[] = [];
  let isProcessingFiles = false;
  let isFileIndicatorVisible = false;
  let fileButtonKey = false;

  function detectYouTubeURL(input: string): boolean {
    const youtubePattern = /(?:https?:\/\/)?(?:www\.)?(?:youtube\.com|youtu\.be)/i;
    return youtubePattern.test(input);
  }

  function handleInput(event: Event) {
    console.log('\n=== Handle Input ===');
    const target = event.target as HTMLTextAreaElement;
    userInput = target.value;
    
    const currentLanguage = get(languageStore);
    
    const languageQualifiers = {
      '--en': 'en',
      '--fr': 'fr',
      '--es': 'es',
      '--de': 'de',
      '--zh': 'zh',
      '--ja': 'ja'
    };

    let detectedLang = '';
    for (const [qualifier, lang] of Object.entries(languageQualifiers)) {
      if (userInput.includes(qualifier)) {
        detectedLang = lang;
        languageStore.set(lang);
        userInput = userInput.replace(new RegExp(`${qualifier}\\s*`), '');
        break;
      }
    }

    console.log('2. Language state:', {
      previousLanguage: currentLanguage,
      currentLanguage: get(languageStore),
      detectedOverride: detectedLang,
      inputAfterLangRemoval: userInput
    });

    isYouTubeURL = detectYouTubeURL(userInput);
    console.log('3. URL detection:', {
      isYouTube: isYouTubeURL,
      pattern: $selectedPatternName,
      systemPromptLength: $systemPrompt?.length
    });
  }

  async function handleFileUpload(e: Event) {
  uploadedFiles = []; // Clear uploadedFiles at the beginning
  if (!files || files.length === 0) return;

  if (uploadedFiles.length >= 5 || (uploadedFiles.length + files.length) > 5) {
    toastStore.trigger({
      message: 'Maximum 5 files allowed',
      background: 'variant-filled-error'
    });
    return;
  }

  isProcessingFiles = true;
  try {
    // Add processing indicator to message store
    messageStore.update(messages => [...messages, {
      role: 'system',
      content: 'Processing files...',
      format: 'loading'
    }]);

    for (let i = 0; i < files.length && uploadedFiles.length < 5; i++) {
      const file = files[i];
      const content = await readFileContent(file);
      fileContents.push(content);
      uploadedFiles = [...uploadedFiles, file.name];
      
      // Update processing status per file
      messageStore.update(messages => {
        const newMessages = [...messages];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage?.format === 'loading') {
          lastMessage.content = `Processing ${file.name} (${file.type})...`;
        }
        return newMessages;
      });
    }

    // Remove processing message on completion
    messageStore.update(messages => 
      messages.filter(m => m.format !== 'loading')
    );

  } catch (error) {
    toastStore.trigger({
      message: 'Error processing files: ' + (error as Error).message,
      background: 'variant-filled-error'
    });
    
    // Clean up processing message on error
    messageStore.update(messages => 
      messages.filter(m => m.format !== 'loading')
    );
  } finally {
    isProcessingFiles = false;
  }
}


  




async function readFileContent(file: File): Promise<string> {
  // Log initial file metadata
  console.log('Reading file:', {
    name: file.name,
    type: file.type,
    size: file.size,
    lastModified: new Date(file.lastModified).toISOString()
  });

  // Handle PDF files
  if (file.type === 'application/pdf') {
    try {
      // Start PDF processing
      console.log('Starting PDF conversion process');
      const markdown = await pdfService.convertToMarkdown(file);
      
      // Validate conversion result
      console.log('PDF conversion completed:', {
        resultLength: markdown.length,
        preview: markdown.substring(0, 100)
      });

      // Ensure we have valid content
      if (!markdown || markdown.trim().length === 0) {
        throw new Error('PDF conversion returned empty content');
      }


      
      // Add to fileContents for pattern processing
      fileContents.push(markdown);

      // Prepare enhanced prompt with system instructions
      const enhancedPrompt = `${$systemPrompt}\nAnalyze and process the provided content according to these instructions.`;
      
      // Format final content with proper labeling
      const finalContent = `${userInput}\n\nFile Contents (PDF):\n${markdown}`;
      
      // Process through pattern system
      await sendMessage(finalContent, enhancedPrompt);

      return markdown;

    } catch (error) {
  console.error('PDF Conversion error:', {
    error,
    fileName: file.name,
    fileSize: file.size
  });
  
  const errorMessage = error instanceof Error 
    ? error.message
    : 'Unknown error during PDF conversion';
    
  throw new Error(`Failed to convert PDF ${file.name}: ${errorMessage}`);
}
  }

  // Handle text files
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    
    reader.onload = async (e) => {
      const content = e.target?.result as string;
      console.log('Text file processed:', {
        fileName: file.name,
        contentLength: content.length,
        preview: content.substring(0, 100)
      });
      // resolve(content);
      const enhancedPrompt = `${$systemPrompt}\nAnalyze and process the provided content according to these instructions.`;
      const finalContent = `${userInput}\n\nFile Contents (Text):\n${content}`;
      await sendMessage(finalContent, enhancedPrompt);
      resolve(content);
    };
    
    reader.onerror = (e) => {
      console.error('FileReader error:', {
        error: reader.error,
        fileName: file.name
      });
      reject(new Error(`Failed to read ${file.name}: ${reader.error?.message}`));
    };

    // Start reading the file
    reader.readAsText(file);
  });
}





  async function saveToObsidian(content: string) {
    if (!$obsidianSettings.saveToObsidian) {
      console.log('Obsidian saving is disabled');
      return;
    }
    
    if (!$obsidianSettings.noteName) {
      toastStore.trigger({
        message: 'Please enter a note name in Obsidian settings',
        background: 'variant-filled-error'
      });
      return;
    }

    if (!$selectedPatternName) {
      toastStore.trigger({
        message: 'No pattern selected',
        background: 'variant-filled-error'
      });
      return;
    }

    if (!content) {
      toastStore.trigger({
        message: 'No content to save',
        background: 'variant-filled-error'
      });
      return;
    }

    try {
      const response = await fetch('/obsidian', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          pattern: $selectedPatternName,
          noteName: $obsidianSettings.noteName,
          content
        })
      });

      const responseData = await response.json();
      
      if (!response.ok) {
        throw new Error(responseData.error || 'Failed to save to Obsidian');
      }
      // Add this after successful save
      updateObsidianSettings({ 
      saveToObsidian: false,  // Reset the save flag
      noteName: ''           // Clear the note name
      });
      toastStore.trigger({
        message: responseData.message || `Saved to Obsidian: ${responseData.fileName}`,
        background: 'variant-filled-success'
      });
    } catch (error) {
      console.error('Failed to save to Obsidian:', error);
      toastStore.trigger({
        message: error instanceof Error ? error.message : 'Failed to save to Obsidian',
        background: 'variant-filled-error'
      });
    }
  }

  // Centralized language instruction logic in ChatService.ts; YouTube flow now passes plain transcript and system prompt
  function extractYouTubeURLs(input: string): string[] {
      const youtubePattern = /(?:https?:\/\/)?(?:www\.)?(?:youtube\.com\/watch\?v=[\w-]+(?:&[^\s]*)?|youtu\.be\/[\w-]+(?:\?[^\s]*)?)/gi;
      return input.match(youtubePattern) || [];
  }

  async function replaceYouTubeURLsWithTranscripts(input: string): Promise<string> {
      const urls = extractYouTubeURLs(input);
      if (urls.length === 0) return input;

      let result = input;
      for (const url of urls) {
          const { transcript } = await getTranscript(url);
          result = result.replace(url, `[YouTube Transcript]:\n${transcript}`);
      }
      return result;
  }

  async function handleSubmit() {
  if (!userInput.trim()) return;

  if (!$authUser) {
    messageStore.update(msgs => [...msgs, {
      role: 'system',
      content: '⚠️ Faça login no Supabase para enviar mensagens e persistir decisões.',
      format: 'plain'
    }]);
    return;
  }

  // Checar API / Gemini antes de enviar
  try {
    const probe = await fetch('/api/health', { signal: AbortSignal.timeout(1500) });
    if (!probe.ok) throw new Error('offline');
    const health = (await probe.json()) as { geminiConfigured?: boolean };
    if (health.geminiConfigured !== true) {
      messageStore.update(msgs => [...msgs, {
        role: 'system',
        content: '⚠️ Chave Gemini não configurada no servidor. No ficheiro `.env` em `tools/arcadia-web`, defina `ARCADIA_GEMINI_API_KEY` (ou `GEMINI_API_KEY`) e reinicie `npm run dev`.',
        format: 'plain'
      }]);
      return;
    }
  } catch {
    messageStore.update(msgs => [...msgs, {
      role: 'system',
      content: '⚠️ API ARCÁDIA indisponível. Confirme que o dev server está a correr (`npm run dev` em tools/arcadia-web) e tente novamente.',
      format: 'plain'
    }]);
    return;
  }

  try {
    console.log('\n=== Submit Handler Start ===');
    let routerDurationMs = 0;
    let fallbackUsed = true;
    let routeSummary = {
      patternName: "summarize",
      strategyName: "",
      reason: "fallback",
      confidence: 0.4
    };
    let assistantOutput = "";

    // Store the user input before any processing
    const inputText = userInput.trim();
    console.log('Captured user input:', inputText);

    // Add the user message to the UI first
    messageStore.update(messages => [...messages, {
      role: 'user',
      content: inputText
    }]);

    // Add loading indicator
    messageStore.update(messages => [...messages, {
      role: 'system',
      content: isYouTubeURL ? 'Processing YouTube video...' : 'Processing...',
      format: 'loading'
    }]);

    // Clear input fields
    userInput = "";
    const hadYouTubeURL = isYouTubeURL;
    isYouTubeURL = false;
    const filesForProcessing = [...uploadedFiles];
    const contentsForProcessing = [...fileContents];
    uploadedFiles = [];
    fileContents = [];
    fileButtonKey = !fileButtonKey;

    // If the message contains YouTube URLs, replace them with transcripts
    let processedText = inputText;
    if (hadYouTubeURL) {
      console.log('Replacing YouTube URLs with transcripts');
      processedText = await replaceYouTubeURLsWithTranscripts(inputText);
    }

    if (autoInterpreterEnabled) {
      messageStore.update(messages => {
        const list = [...messages];
        const loadingIndex = list.findIndex(m => m.format === 'loading');
        if (loadingIndex !== -1) {
          list[loadingIndex] = {
            role: 'system',
            content: 'Interpretando pedido e escolhendo pattern automaticamente...',
            format: 'loading'
          };
        }
        return list;
      });

      const routerStart = Date.now();
      let routeError = false;
      let routeResult = { patternName: 'summarize', strategyName: '', reason: 'fallback', confidence: 0.4, fallback: true };

      try {
        const result = await route(processedText, chatService);
        routeResult = { ...result, fallback: result.confidence < 0.5 };
        routerDurationMs = Date.now() - routerStart;
        fallbackUsed = routeResult.fallback ?? false;
        routeSummary = {
          patternName: result.patternName,
          strategyName: result.strategyName ?? "",
          reason: result.reason,
          confidence: result.confidence
        };

        patternAPI.selectPattern(result.patternName);
        selectedStrategy.set(result.strategyName);

        messageStore.update(messages => {
          const list = [...messages];
          const loadingIndex = list.findIndex(m => m.format === 'loading');
          if (loadingIndex !== -1) list.splice(loadingIndex, 1);
          list.push({
            role: 'system',
            content: `Autopilot: pattern "${result.patternName}"${result.strategyName ? ` + estratégia "${result.strategyName}"` : ''} — ${result.reason} (confiança ${Math.round(result.confidence * 100)}%).`,
            format: 'plain'
          });
          list.push({
            role: 'system',
            content: 'Executando resposta...',
            format: 'loading'
          });
          return list;
        });
      } catch (error) {
        routeError = true;
        if (!$selectedPatternName) patternAPI.selectPattern('summarize');
        selectedStrategy.set('');
      } finally {
        if (!routerDurationMs) routerDurationMs = Date.now() - routerStart;
        recordEvent({
          patternName: routeResult.patternName,
          strategyName: routeResult.strategyName,
          confidence: routeResult.confidence,
          wasRefined: false,
          durationMs: Date.now() - routerStart,
          fallback: routeResult.fallback ?? false,
          error: routeError,
        });
      }
    }

    // Prepare content with file attachments if any
    const contentWithFiles = contentsForProcessing.length > 0
      ? `${processedText}\n\nFile Contents (${filesForProcessing.map(f => f.endsWith('.pdf') ? 'PDF' : 'Text').join(', ')}):\n${contentsForProcessing.join('\n\n---\n\n')}`
      : processedText;

    const constitutionPrompt = getConstitutionPromptFromStore($activeConstitution);
    const basePrompt = contentsForProcessing.length > 0
      ? `${$systemPrompt}\nAnalyze and process the provided content according to these instructions.`
      : $systemPrompt;
    const enhancedPrompt = constitutionPrompt
      ? `${basePrompt}\n\n${constitutionPrompt}`
      : basePrompt;
    
    console.log('Content to send:', {
      text: contentWithFiles.substring(0, 100) + '...',
      length: contentWithFiles.length,
      hasFiles: contentsForProcessing.length > 0
    });
    
    try {
      // Use Orchestrator to process the input
      messageStore.update(messages => {
        const list = [...messages];
        const loadingIndex = list.findIndex(m => m.format === 'loading');
        if (loadingIndex !== -1) {
          list[loadingIndex] = {
            role: 'system',
            content: '🏛️ ARCÁDIA OS processando com Parlamento Cognitivo...',
            format: 'loading'
          };
        }
        return list;
      });

      const orchestratorResult = await orchestrator.process({
        userId: $authUser.id,
        sessionTitle: get(currentSession) ?? null,
        userInput: contentWithFiles,
        constitution: $activeConstitution,
        metadata: {
          hasFiles: contentsForProcessing.length > 0,
          fileTypes: filesForProcessing.map(f => f.endsWith('.pdf') ? 'PDF' : 'Text'),
          routeSummary
        }
      });

      // Remove loading message and add orchestrator response
      messageStore.update(messages => {
        const newMessages = messages.filter(m => m.format !== 'loading');
        newMessages.push({
          role: 'assistant',
          content: orchestratorResult.response,
          format: 'markdown'
        });
        return newMessages;
      });

      assistantOutput = orchestratorResult.response;

      // Se o parlamento não foi usado, também envia ao modelo Gemini (stream)
      if (!orchestratorResult.requiresParliament) {
        messageStore.update(messages => [...messages, {
          role: 'system',
          content: '💬 Processando também com o modelo de IA...',
          format: 'loading'
        }]);

        // Get the chat stream
        const stream = await chatService.streamChat(contentWithFiles, enhancedPrompt);
        
        // Process the stream
        await chatService.processStream(
          stream,
          (content, response) => {
            assistantOutput += '\n\n---\n\n' + content;
            messageStore.update(messages => {
              const newMessages = [...messages];
              // Remove the loading message
              const loadingIndex = newMessages.findIndex(m => m.format === 'loading');
              if (loadingIndex !== -1) {
                newMessages.splice(loadingIndex, 1);
              }

              const lastMessage = newMessages[newMessages.length - 1];
              if (lastMessage?.role === 'assistant') {
                lastMessage.content += '\n\n---\n\n**Análise complementar (IA):**\n\n' + content;
                lastMessage.format = response?.format;
              } else {
                newMessages.push({
                  role: 'assistant',
                  content: '\n\n---\n\n**Análise complementar (IA):**\n\n' + content,
                  format: response?.format
                });
              }
              return newMessages;
            });
          },

          (error) => {
            // Make sure to remove loading message on error
            messageStore.update(messages => 
              messages.filter(m => m.format !== 'loading')
            );
            console.error('Stream processing error:', error);
            
            // Show error message using a valid format type
            messageStore.update(messages => [...messages, {
              role: 'system',
              content: `Error: ${error instanceof Error ? error.message : String(error)}`,
              format: 'plain'
            }]);
          }
        );
      }

      // Persist decision + execution metrics in Supabase (best-effort)
      try {
        const finalAssistantOutput = assistantOutput || (() => {
          const messages = get(messageStore);
          const lastAssistant = [...messages].reverse().find(m => m.role === "assistant");
          return lastAssistant?.content ?? "";
        })();

        await parliamentPersistence.persistDecision({
          userInput: processedText,
          assistantOutput: finalAssistantOutput,
          sessionTitle: get(currentSession),
          route: routeSummary,
          routerDurationMs,
          fallbackUsed
        });
      } catch (persistError) {
        console.warn("ARCÁDIA persistence warning:", persistError);
      }
    } catch (error) {
      // Make sure to remove loading message on error
      messageStore.update(messages => 
        messages.filter(m => m.format !== 'loading')
      );
      throw error; // Re-throw to be caught by the outer try/catch
    }
  } catch (error) {
    console.error('Chat submission error:', error);
    
    // Make sure to remove loading message on error (redundant but safe)
    messageStore.update(messages => 
      messages.filter(m => m.format !== 'loading')
    );
    
    // Show error message using a valid format type
    messageStore.update(messages => [...messages, {
      role: 'system',
      content: `Error: ${error instanceof Error ? error.message : String(error)}`,
      format: 'plain'
    }]);
  } finally {
    // As a final safety measure, ensure loading message is removed
    messageStore.update(messages => 
      messages.filter(m => m.format !== 'loading')
    );
  }
}

  
/* async function handleSubmit() {
  if (!userInput.trim()) return;

  try {
    console.log('\n=== Submit Handler Start ===');
    
    if (isYouTubeURL) {
      console.log('2a. Starting YouTube flow');
      await processYouTubeURL(userInput);
      return;
    }
    
    const enhancedPrompt = fileContents.length > 0 
      ? `${$systemPrompt}\nAnalyze and process the provided content according to these instructions.`
      : $systemPrompt;
    
    // Hide raw content from display but keep it for processing
    messageStore.update(messages => [...messages, {
      role: 'system',
      content: 'Processing content...',
      format: 'loading'
    }]);
    
    // Store the user input before clearing it
    const inputText = userInput;
    
    // Construct finalContent BEFORE clearing userInput
    const finalContent = fileContents.length > 0 
      ? `${inputText}\n\nFile Contents (${uploadedFiles.map(f => f.endsWith('.pdf') ? 'PDF' : 'Text').join(', ')}):\n${fileContents.join('\n\n---\n\n')}`
      : inputText;
    
    // Now clear the input fields
    userInput = ""; 
    uploadedFiles = []; 
    fileContents = []; 
    fileButtonKey = !fileButtonKey; 
     
    await sendMessage(finalContent, enhancedPrompt);
    
  } catch (error) {
    console.error('Chat submission error:', error);
  }
} */

 


  const MAX_CHARS = 8000;

  function handleKeydown(event: KeyboardEvent) {
    // Enter envia; Ctrl+Enter ou Shift+Enter quebra linha
    if (event.key === 'Enter' && !event.shiftKey && !event.ctrlKey) {
      event.preventDefault();
      handleSubmit();
    }
  }

  $: charCount = userInput.length;
  $: charWarning = charCount > MAX_CHARS * 0.85;
  $: charOver = charCount > MAX_CHARS;

  onMount(() => {});

  const cfoPrompts = [
    'Analisar os meus gastos do último mês por categoria',
    'Planejar uma viagem em julho com orçamento 50/30/20',
    'Estratégia para pagar cartão de crédito mais rápido',
    'Quanto devo ter na reserva de emergência?',
  ];

  function applyQuickPrompt(text: string) {
    userInput = text;
  }
</script>

<div class="h-full flex flex-col p-2 gap-2">
  <div class="flex flex-wrap gap-1.5 px-1">
    {#each cfoPrompts as p}
      <button
        type="button"
        class="text-[11px] px-2 py-1 rounded-full border border-white/10 bg-primary-900/40 text-slate-200 hover:bg-primary-800/60 transition-colors"
        on:click={() => applyQuickPrompt(p)}
      >
        {p.length > 52 ? p.slice(0, 50) + '…' : p}
      </button>
    {/each}
  </div>
  <div class="relative flex-1 min-h-0 bg-primary-800/30 rounded-lg">
    <Textarea
      bind:value={userInput}
      on:input={handleInput}
      on:keydown={handleKeydown}
      placeholder="Digite aqui... (Enter envia · Shift+Enter nova linha)"
      maxlength={MAX_CHARS}
      class="w-full h-full resize-none bg-transparent border-none text-sm focus:ring-0 transition-colors p-3 pb-[52px]"
    />
    <!-- char counter -->
    {#if charCount > 0}
      <span class="char-counter" class:char-warn={charWarning} class:char-over={charOver}>
        {charCount}/{MAX_CHARS}
      </span>
    {/if}

    <div class="absolute bottom-3 right-3 flex items-center gap-2">
      <div class="flex items-center gap-2">
        {#if uploadedFiles.length > 0}
          <span class="text-xs text-white/70">{uploadedFiles.length} arquivo{uploadedFiles.length > 1 ? 's' : ''}</span>
        {/if}
      {#key fileButtonKey}
        <FileButton
          name="file-upload"
          button="btn-icon variant-ghost"
          bind:files
          on:change={handleFileUpload}
          disabled={isProcessingFiles || uploadedFiles.length >= 5}
          class="h-10 w-10 bg-primary-800/30 hover:bg-primary-800/50 rounded-full transition-colors"
        >
          <Paperclip class="w-5 h-5" />
        </FileButton>
      {/key}
        <Button
          type="button"
          variant="ghost"
          size="icon"
          name="send"
          on:click={handleSubmit}
          disabled={isProcessingFiles || !userInput.trim() || charOver}
          class="h-10 w-10 bg-primary-800/30 hover:bg-primary-800/50 rounded-full transition-colors disabled:opacity-30"
          title="Enviar (Enter)"
        >
          <Send class="w-5 h-5" />
        </Button>
      </div>
    </div>
  </div>
</div>

<style>
  :global(textarea) {
    scrollbar-width: thin;
    scrollbar-color: rgba(255, 255, 255, 0.2) transparent;
  }

  :global(textarea::-webkit-scrollbar) {
    width: 6px;
  }

  :global(textarea::-webkit-scrollbar-track) {
    background: transparent;
  }

  :global(textarea::-webkit-scrollbar-thumb) {
    background-color: rgba(255, 255, 255, 0.2);
    border-radius: 3px;
  }

  :global(textarea::-webkit-scrollbar-thumb:hover) {
    background-color: rgba(255, 255, 255, 0.3);
  }

  :global(textarea::selection) {
    background-color: rgba(255, 255, 255, 0.1);
  }

  .char-counter {
    position: absolute;
    bottom: 14px;
    left: 12px;
    font-size: 0.65rem;
    color: rgba(148, 163, 184, 0.5);
    pointer-events: none;
    transition: color 0.2s;
  }
  .char-warn { color: rgba(251, 191, 36, 0.8); }
  .char-over { color: rgba(248, 113, 113, 0.9); }
</style>
