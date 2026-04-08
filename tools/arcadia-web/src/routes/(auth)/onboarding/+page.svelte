<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { Button } from '$lib/components/ui/button';
  import { authUser } from '$lib/store/auth-store';
  import { activeConstitution } from '$lib/store/constitution-store';
  import { getSupabaseClient } from '$lib/services/SupabaseClient';

  let currentStep = 1;
  let totalSteps = 4;
  let isCompleting = false;

  async function completeOnboarding() {
    if (!$authUser) return;
    
    isCompleting = true;
    const supabase = getSupabaseClient();
    
    if (supabase) {
      try {
        // Mark onboarding as completed in user profile
        await supabase
          .from('users')
          .update({ 
            onboarding_completed: true,
            updated_at: new Date().toISOString()
          })
          .eq('id', $authUser.id);
      } catch (error) {
        console.error('Erro ao completar onboarding:', error);
      }
    }
    
    // Redirect to dashboard
    goto('/dashboard');
  }

  function nextStep() {
    if (currentStep < totalSteps) {
      currentStep++;
    } else {
      completeOnboarding();
    }
  }

  function prevStep() {
    if (currentStep > 1) {
      currentStep--;
    }
  }

  function skipOnboarding() {
    completeOnboarding();
  }
</script>

<svelte:head>
  <title>Bem-vindo ao ARCÁDIA OS</title>
</svelte:head>

<div class="min-h-screen bg-gradient-to-br from-primary-500/20 via-tertiary-500/20 to-secondary-500/20 flex items-center justify-center p-4">
  <div class="max-w-4xl w-full">
    <!-- Progress bar -->
    <div class="mb-8">
      <div class="flex items-center justify-between mb-2">
        <span class="text-sm text-surface-600">Passo {currentStep} de {totalSteps}</span>
        <Button variant="ghost" size="sm" on:click={skipOnboarding}>
          Pular introdução →
        </Button>
      </div>
      <div class="w-full bg-surface-200 dark:bg-surface-700 rounded-full h-2">
        <div 
          class="bg-gradient-to-r from-primary-500 to-secondary-500 h-2 rounded-full transition-all duration-500"
          style="width: {(currentStep / totalSteps) * 100}%"
        ></div>
      </div>
    </div>

    <!-- Step content -->
    <div class="bg-surface-50 dark:bg-surface-800 rounded-lg p-8 shadow-lg">
      {#if currentStep === 1}
        <!-- Welcome -->
        <div class="text-center">
          <div class="text-6xl mb-6">🏛️</div>
          <h1 class="text-4xl font-bold text-primary-500 mb-4">Bem-vindo ao ARCÁDIA OS</h1>
          <p class="text-xl text-surface-600 mb-6">
            Sistema Operacional de Decisão Autônoma
          </p>
          <div class="max-w-2xl mx-auto text-surface-600 space-y-4">
            <p>
              O ARCÁDIA OS é um sistema inteligente que transforma suas finanças e produtividade 
              em uma infraestrutura de execução contínua, regida por uma constituição ética auto-adaptativa.
            </p>
            <p class="font-medium text-primary-400">
              Máximo ganho financeiro e produtivo com carga mental mínima.
            </p>
          </div>
        </div>
      {:else if currentStep === 2}
        <!-- Parliament System -->
        <div class="text-center">
          <div class="text-6xl mb-6">🗣️</div>
          <h2 class="text-3xl font-bold text-primary-500 mb-4">Parlamento Cognitivo</h2>
          <div class="max-w-2xl mx-auto text-surface-600 space-y-4">
            <p>
              Cada decisão importante é analisada pelo <strong>Parlamento ARCÁDIA — Finanças Pessoais High-End</strong>, com 5 agentes:
            </p>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6 text-left">
              <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4">
                <h3 class="font-semibold text-[#00D4A5] mb-2">Analista de Orçamento</h3>
                <p class="text-sm">50/30/20, categorias e aderência ao plano</p>
              </div>
              <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4">
                <h3 class="font-semibold text-[#7C3AED] mb-2">Psicólogo de Gastos</h3>
                <p class="text-sm">Impulsos, stress e hábitos sustentáveis</p>
              </div>
              <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4">
                <h3 class="font-semibold text-primary-400 mb-2">Estrategista Dívidas & Investimentos</h3>
                <p class="text-sm">Custo da dívida vs retorno esperado</p>
              </div>
              <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4">
                <h3 class="font-semibold text-primary-400 mb-2">Guardião da Reserva</h3>
                <p class="text-sm">Liquidez e colchão de emergência</p>
              </div>
            </div>
            <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4 mt-4">
              <h3 class="font-semibold text-primary-400 mb-2">Otimizador de Fluxo de Caixa</h3>
              <p class="text-sm">Entradas, saídas e previsibilidade</p>
            </div>
          </div>
        </div>
      {:else if currentStep === 3}
        <!-- Constitution -->
        <div class="text-center">
          <div class="text-6xl mb-6">📜</div>
          <h2 class="text-3xl font-bold text-primary-500 mb-4">Sua Constituição Financeira</h2>
          <div class="max-w-2xl mx-auto text-surface-600 space-y-4">
            <p>
              Sua constituição define os princípios e regras que guiam todas as decisões do sistema.
            </p>
            {#if $activeConstitution}
              <div class="bg-emerald-500/10 border border-emerald-500/20 rounded-lg p-4">
                <h3 class="font-semibold text-emerald-400 mb-2">✅ Constituição Ativa</h3>
                <p class="text-sm text-emerald-300">
                  <strong>{$activeConstitution.title}</strong> (versão {$activeConstitution.version})
                </p>
                <p class="text-xs text-emerald-200 mt-2">
                  Sua constituição foi criada automaticamente e pode ser editada a qualquer momento.
                </p>
              </div>
            {:else}
              <div class="bg-yellow-500/10 border border-yellow-500/20 rounded-lg p-4">
                <h3 class="font-semibold text-yellow-400 mb-2">⏳ Criando Constituição</h3>
                <p class="text-sm text-yellow-300">
                  Sua constituição está sendo preparada automaticamente...
                </p>
              </div>
            {/if}
            <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4 text-left">
              <h4 class="font-semibold mb-2">A constituição inclui:</h4>
              <ul class="text-sm space-y-1">
                <li>• Princípios fundamentais de investimento</li>
                <li>• Regras de gestão de risco</li>
                <li>• Limites de gastos e alocação</li>
                <li>• Objetivos financeiros de longo prazo</li>
              </ul>
            </div>
          </div>
        </div>
      {:else if currentStep === 4}
        <!-- How to use -->
        <div class="text-center">
          <div class="text-6xl mb-6">🚀</div>
          <h2 class="text-3xl font-bold text-primary-500 mb-4">Como Usar o ARCÁDIA OS</h2>
          <div class="max-w-2xl mx-auto text-surface-600 space-y-6">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4 text-left">
                <h3 class="font-semibold text-primary-400 mb-2">💬 Chat Inteligente</h3>
                <p class="text-sm">
                  Faça perguntas sobre investimentos, gastos ou decisões financeiras. 
                  O sistema automaticamente aciona o Parlamento quando necessário.
                </p>
              </div>
              <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4 text-left">
                <h3 class="font-semibold text-primary-400 mb-2">📊 Dashboard</h3>
                <p class="text-sm">
                  Acompanhe KPIs financeiros, status do sistema e suas últimas decisões 
                  em tempo real.
                </p>
              </div>
              <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4 text-left">
                <h3 class="font-semibold text-primary-400 mb-2">📜 Constituição</h3>
                <p class="text-sm">
                  Edite seus princípios e regras financeiras para personalizar 
                  completamente o comportamento do sistema.
                </p>
              </div>
              <div class="bg-surface-100 dark:bg-surface-700 rounded-lg p-4 text-left">
                <h3 class="font-semibold text-primary-400 mb-2">📈 Histórico</h3>
                <p class="text-sm">
                  Revise todas as decisões tomadas, com detalhes completos do processo 
                  de deliberação parlamentar.
                </p>
              </div>
            </div>
            <div class="bg-primary-500/10 border border-primary-500/20 rounded-lg p-4">
              <h3 class="font-semibold text-primary-400 mb-2">🎯 Exemplo de Uso</h3>
              <p class="text-sm text-left">
                Pergunte: <em>"Devo investir R$ 10.000 em ações ou deixar na poupança?"</em>
                <br><br>
                O sistema automaticamente:
                <br>• Aciona o Parlamento Cognitivo
                <br>• Analisa com base na sua constituição
                <br>• Apresenta uma decisão fundamentada
                <br>• Salva tudo no histórico
              </p>
            </div>
          </div>
        </div>
      {/if}

      <!-- Navigation buttons -->
      <div class="flex items-center justify-between mt-8">
        <Button 
          variant="ghost" 
          on:click={prevStep}
          disabled={currentStep === 1}
        >
          ← Anterior
        </Button>
        
        <div class="flex gap-2">
          {#each Array.from({length: totalSteps}, (_, i) => i + 1) as step}
            <div 
              class="w-3 h-3 rounded-full transition-colors {step === currentStep ? 'bg-primary-500' : step < currentStep ? 'bg-primary-300' : 'bg-surface-300'}"
            ></div>
          {/each}
        </div>
        
        <Button 
          on:click={nextStep}
          disabled={isCompleting}
        >
          {#if currentStep === totalSteps}
            {isCompleting ? 'Finalizando...' : 'Começar →'}
          {:else}
            Próximo →
          {/if}
        </Button>
      </div>
    </div>

    <!-- Footer -->
    <div class="text-center mt-6 text-sm text-surface-500">
      <p>
        Você pode acessar este tutorial novamente a qualquer momento através do menu de ajuda.
      </p>
    </div>
  </div>
</div>