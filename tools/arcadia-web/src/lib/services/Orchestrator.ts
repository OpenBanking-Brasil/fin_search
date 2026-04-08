/**
 * Orchestrator.ts - Sistema de Orquestração do ARCÁDIA OS
 * 
 * Coordena o fluxo de decisões entre diferentes componentes:
 * - Análise de entrada
 * - Roteamento para Parliament quando necessário
 * - Execução de decisões
 * - Persistência de resultados
 */

import Parliament, { type ParliamentDecision } from './Parliament';
import { ParliamentPersistenceService } from './ParliamentPersistenceService';
import type { ConstitutionRecord } from '$lib/store/constitution-store';

export interface ProcessingContext {
  userId: string;
  sessionTitle: string | null;
  userInput: string;
  constitution: ConstitutionRecord | null;
  requiresParliament?: boolean;
  metadata?: Record<string, any>;
}

export interface ProcessingResult {
  requiresParliament: boolean;
  parliamentDecision?: ParliamentDecision;
  response: string;
  confidence: number;
  actionItems?: string[];
  risks?: string[];
  nextSteps?: string[];
}

export class Orchestrator {
  private parliament: Parliament;
  private persistenceService: ParliamentPersistenceService;

  constructor() {
    this.parliament = new Parliament();
    this.persistenceService = new ParliamentPersistenceService();
  }

  /**
   * Processa uma entrada do usuário e determina o fluxo apropriado
   */
  async process(context: ProcessingContext): Promise<ProcessingResult> {
    // 1. Analisa se a entrada requer deliberação parlamentar
    const requiresParliament = this.shouldInvolveParliament(context.userInput);

    if (requiresParliament) {
      // 2. Conduz debate parlamentar
      const parliamentDecision = await this.parliament.debate(
        context.userInput,
        context.constitution,
        3 // máximo 3 rodadas
      );

      // 3. Gera resposta baseada na decisão parlamentar
      const response = this.generateParliamentaryResponse(parliamentDecision);

      // 4. Persiste a decisão
      await this.persistDecision(context, parliamentDecision, response);

      return {
        requiresParliament: true,
        parliamentDecision,
        response,
        confidence: parliamentDecision.confidence,
        actionItems: parliamentDecision.executionPlan,
        risks: parliamentDecision.risks,
        nextSteps: this.generateNextSteps(parliamentDecision)
      };
    } else {
      // Fluxo simples sem parliament
      const response = this.generateSimpleResponse(context);
      
      return {
        requiresParliament: false,
        response: response.text,
        confidence: response.confidence,
        actionItems: response.actionItems,
        nextSteps: response.nextSteps
      };
    }
  }

  /**
   * Determina se uma entrada requer deliberação parlamentar
   */
  private shouldInvolveParliament(input: string): boolean {
    const parliamentTriggers = [
      // Palavras-chave financeiras que indicam decisões importantes
      'investir', 'investimento', 'comprar', 'vender', 'aplicar',
      'empréstimo', 'financiamento', 'crédito', 'dívida',
      'aposentadoria', 'previdência', 'poupança',
      'risco', 'retorno', 'rentabilidade',
      'orçamento', 'gastos', 'economizar',
      'seguro', 'proteção', 'reserva',
      'negócio', 'empreender', 'empresa',
      
      // Frases que indicam tomada de decisão
      'devo', 'deveria', 'vale a pena', 'é melhor',
      'recomenda', 'sugere', 'aconselha',
      'decidir', 'escolher', 'optar',
      'planejar', 'estratégia', 'meta',
      
      // Valores monetários
      'reais', 'r$', 'mil', 'milhão', 'dinheiro', 'valor'
    ];

    const lowerInput = input.toLowerCase();
    return parliamentTriggers.some(trigger => lowerInput.includes(trigger));
  }

  /**
   * Gera resposta baseada na decisão parlamentar
   */
  private generateParliamentaryResponse(decision: ParliamentDecision): string {
    let response = `## 🏛️ Parlamento ARCÁDIA — Finanças Pessoais High-End\n\n*O teu CFO pessoal em modo deliberação.*\n\n`;
    
    // Status da decisão
    const statusEmoji = {
      'approved': '✅',
      'rejected': '❌',
      'modified': '⚠️'
    }[decision.finalDecision];
    
    const statusText = {
      'approved': 'APROVADA',
      'rejected': 'REJEITADA',
      'modified': 'APROVADA COM MODIFICAÇÕES'
    }[decision.finalDecision];
    
    response += `**Status:** ${statusEmoji} ${statusText}\n`;
    response += `**Consenso:** ${decision.consensusLevel}% dos agentes\n`;
    response += `**Confiança:** ${decision.confidence}%\n\n`;
    
    // Resultado esperado
    response += `### 🎯 Resultado Esperado\n${decision.expectedOutcome}\n\n`;
    
    // Modificações (se houver)
    if (decision.modifications && decision.modifications.length > 0) {
      response += `### ⚠️ Modificações Recomendadas\n`;
      decision.modifications.forEach(mod => {
        response += `- ${mod}\n`;
      });
      response += '\n';
    }
    
    // Plano de execução
    if (decision.executionPlan && decision.executionPlan.length > 0) {
      response += `### 📋 Plano de Execução\n`;
      decision.executionPlan.forEach((step, index) => {
        response += `${index + 1}. ${step}\n`;
      });
      response += '\n';
    }
    
    // Riscos identificados
    if (decision.risks.length > 0) {
      response += `### ⚠️ Riscos Identificados\n`;
      decision.risks.forEach(risk => {
        response += `- ${risk}\n`;
      });
      response += '\n';
    }
    
    // Debate resumido
    response += `### 🗣️ Resumo do Debate (${decision.rounds.length} rodadas)\n`;
    const lastRound = decision.rounds[decision.rounds.length - 1];
    
    lastRound.arguments.forEach(arg => {
      const agent = this.parliament.getAgents().find(a => a.id === arg.agentId);
      const positionEmoji = {
        'favor': '👍',
        'against': '👎',
        'neutral': '🤔'
      }[arg.position];
      
      response += `**${agent?.name}** ${positionEmoji} (${arg.confidence}% confiança)\n`;
      response += `${arg.reasoning}\n\n`;
    });
    
    // Opiniões dissidentes
    if (decision.dissent && decision.dissent.length > 0) {
      response += `### 🗯️ Opiniões Dissidentes\n`;
      decision.dissent.forEach(dissent => {
        response += `- ${dissent}\n`;
      });
    }
    
    return response;
  }

  /**
   * Gera resposta simples para entradas que não requerem parliament
   */
  private generateSimpleResponse(context: ProcessingContext): {
    text: string;
    confidence: number;
    actionItems?: string[];
    nextSteps?: string[];
  } {
    const input = context.userInput.toLowerCase();
    
    // Respostas para perguntas simples
    if (input.includes('como') && input.includes('funciona')) {
      return {
        text: `## 🤖 ARCÁDIA OS - Resposta Direta\n\nSua pergunta não requer deliberação parlamentar. Baseado na sua constituição financeira, posso fornecer orientação direta.\n\nPara questões mais complexas que envolvam decisões financeiras importantes, o sistema automaticamente acionará o Parlamento Cognitivo para uma análise multi-perspectiva.`,
        confidence: 85,
        nextSteps: ['Considere fazer uma pergunta mais específica sobre decisões financeiras']
      };
    }
    
    if (input.includes('status') || input.includes('situação')) {
      return {
        text: `## 📊 Status do Sistema\n\nO ARCÁDIA OS está operacional e sua constituição financeira está ativa. Para análises detalhadas de sua situação financeira, visite o Dashboard.\n\nO sistema está pronto para auxiliar em decisões financeiras complexas através do Parlamento Cognitivo.`,
        confidence: 90,
        actionItems: ['Acesse o Dashboard para métricas detalhadas'],
        nextSteps: ['Faça perguntas específicas sobre investimentos ou gastos']
      };
    }
    
    // Resposta genérica
    return {
      text: `## 💬 Resposta Direta\n\nSua mensagem foi processada, mas não requer análise parlamentar. Para decisões financeiras importantes, use termos como "investir", "comprar", "devo", ou mencione valores específicos.\n\nO Parlamento Cognitivo será automaticamente acionado para questões que envolvam:\n- Investimentos e aplicações\n- Grandes gastos ou compras\n- Decisões de endividamento\n- Planejamento financeiro\n- Mudanças na estratégia de investimento`,
      confidence: 70,
      nextSteps: [
        'Seja mais específico sobre decisões financeiras',
        'Use valores monetários para acionar análise detalhada',
        'Consulte o Dashboard para métricas atuais'
      ]
    };
  }

  /**
   * Persiste a decisão no banco de dados
   */
  private async persistDecision(
    context: ProcessingContext,
    decision: ParliamentDecision,
    assistantOutput: string,
  ): Promise<void> {
    try {
      await this.persistenceService.persistDecision({
        userInput: context.userInput,
        assistantOutput,
        sessionTitle: context.sessionTitle,
        route: {
          patternName: 'parliament',
          strategyName: decision.finalDecision,
          reason: `Parliament: tipo ${this.extractDecisionType(context.userInput)} · consenso ${decision.consensusLevel}%`,
          confidence: Math.min(1, Math.max(0, decision.confidence / 100)),
        },
        routerDurationMs: 0,
        fallbackUsed: false,
      });
    } catch (error) {
      console.error('Erro ao persistir decisão:', error);
      // Não falha o fluxo principal se a persistência falhar
    }
  }

  /**
   * Extrai o tipo de decisão da entrada
   */
  private extractDecisionType(input: string): string {
    const lowerInput = input.toLowerCase();
    
    if (lowerInput.includes('investir') || lowerInput.includes('investimento')) return 'investment';
    if (lowerInput.includes('comprar') || lowerInput.includes('compra')) return 'purchase';
    if (lowerInput.includes('vender') || lowerInput.includes('venda')) return 'sale';
    if (lowerInput.includes('empréstimo') || lowerInput.includes('financiamento')) return 'financing';
    if (lowerInput.includes('poupança') || lowerInput.includes('reserva')) return 'savings';
    if (lowerInput.includes('seguro') || lowerInput.includes('proteção')) return 'insurance';
    if (lowerInput.includes('orçamento') || lowerInput.includes('gastos')) return 'budgeting';
    
    return 'general';
  }

  /**
   * Estima impacto financeiro da decisão
   */
  private estimateFinancialImpact(input: string): number | null {
    // Regex para encontrar valores monetários
    const moneyRegex = /(?:r\$|reais?)\s*(\d+(?:\.\d{3})*(?:,\d{2})?)|(\d+(?:\.\d{3})*(?:,\d{2})?)\s*(?:r\$|reais?)/gi;
    const matches = input.match(moneyRegex);
    
    if (matches && matches.length > 0) {
      // Pega o primeiro valor encontrado e converte para número
      const valueStr = matches[0].replace(/[^\d,]/g, '').replace(',', '.');
      const value = parseFloat(valueStr);
      return isNaN(value) ? null : value;
    }
    
    return null;
  }

  /**
   * Gera próximos passos baseados na decisão
   */
  private generateNextSteps(decision: ParliamentDecision): string[] {
    const steps: string[] = [];
    
    if (decision.finalDecision === 'approved') {
      steps.push('Executar plano conforme aprovado pelo parlamento');
      steps.push('Monitorar resultados e métricas de performance');
    } else if (decision.finalDecision === 'modified') {
      steps.push('Implementar modificações sugeridas');
      steps.push('Reapresentar proposta modificada se necessário');
    } else {
      steps.push('Revisar proposta com base nos riscos identificados');
      steps.push('Buscar alternativas que atendam aos critérios constitucionais');
    }
    
    steps.push('Acompanhar impacto no dashboard');
    steps.push('Atualizar constituição se necessário');
    
    return steps;
  }
}

export default Orchestrator;