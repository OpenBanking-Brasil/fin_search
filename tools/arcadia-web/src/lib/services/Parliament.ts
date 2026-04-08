/**
 * Parlamento ARCÁDIA OS — Finanças Pessoais High-End
 * Cinco agentes especializados + orquestração de debate.
 */

export interface Agent {
	id: string;
	name: string;
	role: string;
	personality: string;
	expertise: string[];
	bias: string;
}

export interface Argument {
	agentId: string;
	position: "favor" | "against" | "neutral";
	reasoning: string;
	evidence: string[];
	confidence: number;
	risks: string[];
	benefits: string[];
}

export interface DebateRound {
	round: number;
	arguments: Argument[];
	consensus: number;
}

export interface ParliamentDecision {
	proposal: string;
	rounds: DebateRound[];
	finalDecision: "approved" | "rejected" | "modified";
	modifications?: string[];
	consensusLevel: number;
	executionPlan?: string[];
	risks: string[];
	expectedOutcome: string;
	confidence: number;
	dissent?: string[];
}

export class Parliament {
	private agents: Agent[] = [
		{
			id: "budget_analyst",
			name: "Analista de Orçamento",
			role: "Orçamento 50/30/20 e categorias",
			personality: "Metódico, detalhista, focado em aderência ao plano",
			expertise: [
				"orçamento mensal",
				"categorização",
				"limites por rubrica",
			],
			bias: "Prioriza disciplina sobre impulsos",
		},
		{
			id: "spending_psychologist",
			name: "Psicólogo de Gastos",
			role: "Comportamento e gatilhos emocionais",
			personality: "Empático, direto, sem julgamento",
			expertise: [
				"controle emocional",
				"gastos impulsivos",
				"hábitos",
			],
			bias: "Protege bem-estar e sustentabilidade do plano",
		},
		{
			id: "debt_investment_strategist",
			name: "Estrategista de Dívidas e Investimentos",
			role: "Custo da dívida vs retorno esperado",
			personality: "Analítico, estruturado, longo prazo",
			expertise: ["quitação de dívidas", "alocação", "investimento"],
			bias: "Minimiza juros reais e maximiza consistência",
		},
		{
			id: "emergency_guardian",
			name: "Guardião da Reserva de Emergência",
			role: "Liquidez e colchão de segurança",
			personality: "Conservador, protetor, claro nos trade-offs",
			expertise: ["reserva de emergência", "imprevistos", "liquidez"],
			bias: "Não negocia segurança mínima acordada na Constituição",
		},
		{
			id: "cashflow_optimizer",
			name: "Otimizador de Fluxo de Caixa",
			role: "Entradas, saídas e timing",
			personality: "Pragmático, orientado a caixa e previsibilidade",
			expertise: [
				"fluxo de caixa",
				"previsão",
				"ajustes em tempo real",
			],
			bias: "Evita aperto de liquidez e estagnação de capital ocioso",
		},
	];

	async debate(
		proposal: string,
		constitution: unknown,
		maxRounds: number = 3,
	): Promise<ParliamentDecision> {
		const rounds: DebateRound[] = [];
		let currentConsensus = 0;

		for (let round = 1; round <= maxRounds; round++) {
			const roundArgs: Argument[] = [];

			for (const agent of this.agents) {
				const argument = await this.generateArgument(
					agent,
					proposal,
					constitution,
					rounds,
				);
				roundArgs.push(argument);
			}

			const consensus = this.calculateConsensus(roundArgs);
			rounds.push({ round, arguments: roundArgs, consensus });
			currentConsensus = consensus;
			if (consensus > 85) break;
		}

		return this.finalizeDecision(proposal, rounds);
	}

	private async generateArgument(
		agent: Agent,
		proposal: string,
		constitution: unknown,
		previousRounds: DebateRound[],
	): Promise<Argument> {
		const analysis = this.simulateAgentAnalysis(
			agent,
			proposal,
			constitution,
			previousRounds,
		);
		return {
			agentId: agent.id,
			position: analysis.position as Argument["position"],
			reasoning: analysis.reasoning,
			evidence: analysis.evidence,
			confidence: analysis.confidence,
			risks: analysis.risks,
			benefits: analysis.benefits,
		};
	}

	private simulateAgentAnalysis(
		agent: Agent,
		proposal: string,
		constitution: unknown,
		_previousRounds: DebateRound[],
	) {
		const p = proposal.toLowerCase();
		const base = {
			position: "neutral" as const,
			reasoning: "",
			evidence: [] as string[],
			confidence: 72,
			risks: [] as string[],
			benefits: [] as string[],
		};

		const principles = Array.isArray(
			(constitution as { principles?: string[] } | null)?.principles,
		)
			? ((constitution as { principles: string[] }).principles ?? []).join(
					" ",
				)
			: "";

		switch (agent.id) {
			case "budget_analyst":
				return {
					...base,
					position: (p.includes("orçamento") || p.includes("gasto")
						? "favor"
						: "neutral") as Argument["position"],
					reasoning: `${agent.name}: avalio aderência ao método 50/30/20 e ao teto por categoria. Proposta analisada à luz do orçamento mensal e da Constituição.`,
					evidence: ["Categorias do Arcádia", "Limites definidos pelo utilizador"],
					risks: ["Estouro de categoria", "Subestimação de despesas fixas"],
					benefits: ["Previsibilidade", "Correção rápida no mês seguinte"],
					confidence: 78,
				};
			case "spending_psychologist":
				return {
					...base,
					position: (p.includes("impulso") || p.includes("ansiedade")
						? "against"
						: "neutral") as Argument["position"],
					reasoning: `${agent.name}: identifico gatilhos emocionais (stress, tédio, recompensa). Sugiro pausa e regra de 24h em gastos não essenciais.`,
					evidence: ["Padrões de delivery/lazer", "Regra de pausa da Constituição"],
					risks: ["Gasto por alívio emocional", "normalização de excessos"],
					benefits: ["Menos arrependimento", "hábitos alinhados a valores"],
					confidence: 74,
				};
			case "debt_investment_strategist":
				return {
					...base,
					position: (p.includes("dívida") || p.includes("cartão") || p.includes("invest")
						? "favor"
						: "neutral") as Argument["position"],
					reasoning: `${agent.name}: comparo custo da dívida (juros reais) com retorno esperado e horizonte. Priorizo quitar caro antes de alocar em risco.`,
					evidence: ["Taxas efetivas", "Prazo e liquidez"],
					risks: ["Carregar juros altos", "subestimar volatilidade"],
					benefits: ["Custo financeiro menor", "consistência de aportes"],
					confidence: 80,
				};
			case "emergency_guardian":
				return {
					...base,
					position: (p.includes("reserva") || p.includes("emergência")
						? "favor"
						: "neutral") as Argument["position"],
					reasoning: `${agent.name}: verifico se a reserva cobre imprevistos antes de compromissos adicionais. A Constituição fixa o mínimo não negociável.`,
					evidence: [principles.slice(0, 80) || "Princípios da Constituição"],
					risks: ["Liquidez insuficiente", "imprevisto vira dívida cara"],
					benefits: ["Sono e decisões mais frias", "menor dependência de crédito"],
					confidence: 82,
				};
			case "cashflow_optimizer":
				return {
					...base,
					position: "neutral",
					reasoning: `${agent.name}: modelizo entradas/saídas e saldo futuro 30–90 dias. Ajusto timing de pagamentos e aportes para evitar aperto.`,
					evidence: ["Histórico no Arcádia", "Sazonalidade simples"],
					risks: ["Desalinhamento de datas", "picos de saída"],
					benefits: ["Caixa previsível", "alertas antes do problema"],
					confidence: 76,
				};
			default:
				return base;
		}
	}

	private calculateConsensus(agentArguments: Argument[]): number {
		const positions = agentArguments.map((arg) => arg.position);
		const favorCount = positions.filter((p) => p === "favor").length;
		const againstCount = positions.filter((p) => p === "against").length;
		const total = agentArguments.length;
		const maxAgreement = Math.max(
			favorCount,
			againstCount,
			positions.filter((p) => p === "neutral").length,
		);
		return Math.round((maxAgreement / total) * 100);
	}

	private finalizeDecision(
		proposal: string,
		rounds: DebateRound[],
	): ParliamentDecision {
		const lastRound = rounds[rounds.length - 1];
		const finalConsensus = lastRound.consensus;
		const finalArguments = lastRound.arguments;
		const favorVotes = finalArguments.filter((arg) => arg.position === "favor").length;
		const againstVotes = finalArguments.filter((arg) => arg.position === "against").length;

		let finalDecision: ParliamentDecision["finalDecision"];
		let modifications: string[] = [];

		if (favorVotes > againstVotes) {
			finalDecision = finalConsensus > 60 ? "approved" : "modified";
			if (finalDecision === "modified") {
				modifications = this.generateModifications(finalArguments);
			}
		} else {
			finalDecision = "rejected";
		}

		const allRisks = finalArguments.flatMap((arg) => arg.risks);
		const uniqueRisks = [...new Set(allRisks)];
		const avgConfidence = Math.round(
			finalArguments.reduce((sum, arg) => sum + arg.confidence, 0) /
				finalArguments.length,
		);
		const majorityPosition = favorVotes > againstVotes ? "favor" : "against";
		const dissent = finalArguments
			.filter((arg) => arg.position !== majorityPosition)
			.map(
				(arg) =>
					`${this.agents.find((a) => a.id === arg.agentId)?.name}: ${arg.reasoning}`,
			);

		return {
			proposal,
			rounds,
			finalDecision,
			modifications: modifications.length > 0 ? modifications : undefined,
			consensusLevel: finalConsensus,
			executionPlan: this.generateExecutionPlan(proposal, finalDecision),
			risks: uniqueRisks,
			expectedOutcome: this.generateExpectedOutcome(
				proposal,
				finalDecision,
				avgConfidence,
			),
			confidence: avgConfidence,
			dissent: dissent.length > 0 ? dissent : undefined,
		};
	}

	private generateModifications(agentArguments: Argument[]): string[] {
		const modifications: string[] = [];
		const concerned = agentArguments.filter((arg) => arg.position !== "favor");
		for (const arg of concerned) {
			if (arg.risks.some((r) => r.toLowerCase().includes("liquidez"))) {
				modifications.push("Aumentar colchão de liquidez antes de executar");
			}
			if (arg.agentId === "spending_psychologist") {
				modifications.push("Aplicar pausa de 24h e limite por categoria");
			}
		}
		return [...new Set(modifications)];
	}

	private generateExecutionPlan(
		_proposal: string,
		decision: ParliamentDecision["finalDecision"],
	): string[] {
		if (decision === "rejected") {
			return [
				"Não executar até rever riscos",
				"Ajustar proposta com base no Parlamento",
			];
		}
		return [
			"Registar passos no Arcádia (Planeamento / movimentos)",
			"Atualizar orçamento e alertas no dashboard",
			"Revisão agendada na próxima janela constitucional",
		];
	}

	private generateExpectedOutcome(
		_proposal: string,
		decision: string,
		confidence: number,
	): string {
		if (decision === "rejected") {
			return "Sem execução imediata — manter controlo de caixa e rever premissas.";
		}
		const level = confidence > 80 ? "alta" : confidence > 60 ? "média" : "moderada";
		return `Plano alinhado ao modo Finanças Pessoais High-End com confiança ${level}, sujeito à tua disciplina e aos dados registados no Arcádia.`;
	}

	getAgents(): Agent[] {
		return this.agents;
	}
}

export default Parliament;
