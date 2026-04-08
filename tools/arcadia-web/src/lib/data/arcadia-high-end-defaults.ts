/**
 * ARCÁDIA OS – Finanças Pessoais High-End
 * Constituição padrão e metadados de produto.
 */

export const ARCADIA_PRODUCT_NAME = "ARCÁDIA OS – Finanças Pessoais High-End";

export const DEFAULT_CONSTITUTION_HIGH_END = {
	title: "ARCÁDIA OS – Finanças Pessoais High-End",
	narrative_style: "disciplinado, empático, orientado a liberdade financeira",
	principles: [
		"Raiz: Liberdade financeira — viver de acordo com valores, sem ansiedade por dinheiro.",
		"Orçamento 50/30/20: ~50% necessidades, ~30% desejos, ~20% poupança e quitação de dívidas.",
		"Reserva de emergência antes de investimentos de risco (meta típica: 3–12 meses de despesas essenciais).",
		"Dívidas caras (cartão, cheque especial) têm prioridade sobre consumo discricionário.",
		"Investimento regular, automático quando possível, alinhado ao horizonte e tolerância a risco.",
		"Controle emocional: pausa de 24–48h em compras não essenciais acima de um limite definido.",
		"Transparência: acompanhar entradas, saídas e categorias — o que não é medido não é gerido.",
		"Revisão mensal obrigatória: ajustar orçamento com base na realidade, não na culpa.",
	],
	rules: {
		arcadia_version: "high_end_finance_v1",
		budget_method: "50_30_20",
		emergency_months_target: 6,
		impulse_cooldown_hours: 24,
		debt_priority: "highest_interest_first",
		investment_horizon_years_default: 10,
		spending_limits: {
			delivery_warning_pct_above_rolling_avg: 20,
		},
	},
} as const;

export const CONSTITUTION_STUDIO_SUGGESTIONS = [
	"Priorizar quitação de dívidas com juros reais acima de inflação.",
	"Automatizar transferência para reserva no dia do salário.",
	"Definir teto mensal por categoria (lazer, delivery, assinaturas).",
	"Revisar assinaturas recorrentes a cada trimestre.",
];
