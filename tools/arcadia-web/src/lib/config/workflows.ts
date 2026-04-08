export interface Workflow {
	id: string;
	label: string;
	description: string;
	icon: string;
	patternName: string;
	strategyName: string;
	placeholder: string;
	/** Categoria para agrupamento no seletor */
	category: "geral" | "código" | "finanças" | "conteúdo" | "avançado";
	/** Flags especiais que ativam recursos CLI do Fabric */
	flags?: {
		search?: boolean;
		thinking?: "off" | "low" | "medium" | "high";
		suppressThink?: boolean;
		scrapeUrl?: boolean;
	};
}

export const WORKFLOWS: Workflow[] = [
	// ── Geral ────────────────────────────────────────────────────────────────
	{
		id: "summarize",
		label: "Resumir",
		description: "Resumo objetivo de qualquer conteúdo.",
		icon: "📋",
		patternName: "summarize",
		strategyName: "standard",
		placeholder: "Cole o texto ou URL que quer resumir...",
		category: "geral",
	},
	{
		id: "extract_wisdom",
		label: "Extrair Insights",
		description: "Extraia as ideias mais valiosas de qualquer conteúdo.",
		icon: "💡",
		patternName: "extract_wisdom",
		strategyName: "cot",
		placeholder: "Cole o artigo, vídeo ou texto para extrair insights...",
		category: "geral",
	},
	{
		id: "analyze_claims",
		label: "Analisar Argumentos",
		description: "Valide afirmações e identifique falácias.",
		icon: "⚖️",
		patternName: "analyze_claims",
		strategyName: "cot",
		placeholder: "Cole o texto com afirmações a analisar...",
		category: "geral",
	},
	{
		id: "compare",
		label: "Comparar Opções",
		description: "Compare alternativas e veja prós e contras.",
		icon: "🔀",
		patternName: "compare_and_contrast",
		strategyName: "tot",
		placeholder: "Descreva as opções que quer comparar...",
		category: "geral",
	},
	{
		id: "improve_writing",
		label: "Melhorar Texto",
		description: "Refine clareza, gramática e impacto de qualquer texto.",
		icon: "✍️",
		patternName: "improve_writing",
		strategyName: "self-refine",
		placeholder: "Cole o texto que quer melhorar...",
		category: "geral",
	},
	{
		id: "create_plan",
		label: "Criar Plano",
		description: "Gere um plano estruturado e acionável.",
		icon: "🗂️",
		patternName: "create_design_document",
		strategyName: "cot",
		placeholder: "Descreva o objetivo ou projeto que quer planejar...",
		category: "geral",
	},
	// ── Código ───────────────────────────────────────────────────────────────
	{
		id: "explain_code",
		label: "Explicar Código",
		description: "Entenda qualquer trecho de código em linguagem simples.",
		icon: "🔍",
		patternName: "explain_code",
		strategyName: "cot",
		placeholder: "Cole o código aqui...",
		category: "código",
	},
	{
		id: "review_code",
		label: "Revisar Código",
		description: "Identifique bugs, problemas e melhore qualidade.",
		icon: "🛠️",
		patternName: "review_code",
		strategyName: "self-refine",
		placeholder: "Cole o código para revisão...",
		category: "código",
	},
	{
		id: "create_coding_feature",
		label: "Criar Feature",
		description: "Planeje e implemente uma nova funcionalidade.",
		icon: "⚙️",
		patternName: "create_coding_feature",
		strategyName: "cot",
		placeholder: "Descreva a feature a implementar e cole o contexto do código...",
		category: "código",
	},
	// ── Finanças (Open Finance) ───────────────────────────────────────────────
	{
		id: "finance_analyze",
		label: "Analisar Investimento",
		description: "Analise opções de investimento com raciocínio estruturado.",
		icon: "📈",
		patternName: "analyze_claims",
		strategyName: "cot",
		placeholder: "Descreva o investimento, taxas e prazo para análise...",
		category: "finanças",
	},
	{
		id: "finance_compare",
		label: "Comparar Produtos",
		description: "Compare CDBs, fundos, LCIs e outros produtos financeiros.",
		icon: "💰",
		patternName: "compare_and_contrast",
		strategyName: "tot",
		placeholder: "Descreva os produtos financeiros a comparar (taxas, prazo, liquidez)...",
		category: "finanças",
	},
	{
		id: "finance_explain",
		label: "Explicar Conceito",
		description: "Entenda termos financeiros: CDI, IPCA, Selic, etc.",
		icon: "🏦",
		patternName: "explain_code",
		strategyName: "cot",
		placeholder: "Qual conceito financeiro quer entender?",
		category: "finanças",
	},
	{
		id: "finance_report",
		label: "Relatório Financeiro",
		description: "Gere relatório estruturado a partir de dados financeiros.",
		icon: "📊",
		patternName: "create_summary",
		strategyName: "standard",
		placeholder: "Cole os dados ou extrato financeiro para o relatório...",
		category: "finanças",
	},
	{
		id: "finance_risk",
		label: "Análise de Risco",
		description: "Avalie riscos de um portfólio ou operação.",
		icon: "🛡️",
		patternName: "analyze_paper",
		strategyName: "reflexion",
		placeholder: "Descreva o portfólio ou operação para análise de risco...",
		category: "finanças",
	},
	// ── Conteúdo / Pesquisa ───────────────────────────────────────────────────
	{
		id: "youtube_insights",
		label: "YouTube → Insights",
		description: "Extraia sabedoria de qualquer vídeo do YouTube.",
		icon: "▶️",
		patternName: "extract_wisdom",
		strategyName: "cot",
		placeholder: "Cole a URL do vídeo do YouTube...",
		category: "conteúdo",
	},
	{
		id: "youtube_summarize",
		label: "YouTube → Resumo",
		description: "Resumo rápido de qualquer vídeo ou podcast.",
		icon: "🎬",
		patternName: "summarize",
		strategyName: "standard",
		placeholder: "Cole a URL do vídeo do YouTube...",
		category: "conteúdo",
	},
	{
		id: "web_analyze",
		label: "Analisar Página Web",
		description: "Analise e extraia insights de qualquer site via Jina AI.",
		icon: "🌐",
		patternName: "analyze_claims",
		strategyName: "cot",
		placeholder: "Cole a URL do site para analisar...",
		category: "conteúdo",
		flags: { scrapeUrl: true },
	},
	{
		id: "web_summarize",
		label: "Resumir Página Web",
		description: "Resumo objetivo de qualquer página via Jina AI.",
		icon: "🔗",
		patternName: "summarize",
		strategyName: "standard",
		placeholder: "Cole a URL para resumir...",
		category: "conteúdo",
		flags: { scrapeUrl: true },
	},
	{
		id: "research",
		label: "Pesquisa com Busca Web",
		description: "Pesquise e analise usando busca web em tempo real.",
		icon: "🔎",
		patternName: "analyze_paper",
		strategyName: "cot",
		placeholder: "Qual tema quer pesquisar? (busca web ativa)",
		category: "conteúdo",
		flags: { search: true },
	},
	// ── Avançado ─────────────────────────────────────────────────────────────
	{
		id: "deep_analysis",
		label: "Análise Profunda",
		description: "Análise com raciocínio avançado (thinking mode).",
		icon: "🧠",
		patternName: "analyze_claims",
		strategyName: "reflexion",
		placeholder: "Descreva o problema complexo para análise profunda...",
		category: "avançado",
		flags: { thinking: "high", suppressThink: true },
	},
	{
		id: "multi_path",
		label: "Múltiplos Caminhos",
		description: "Tree of Thought: gere e compare múltiplas abordagens.",
		icon: "🌳",
		patternName: "compare_and_contrast",
		strategyName: "tot",
		placeholder: "Descreva o problema para explorar múltiplas soluções...",
		category: "avançado",
	},
	{
		id: "self_consistent",
		label: "Autoconsistência",
		description: "Múltiplos raciocínios com consenso para maior precisão.",
		icon: "🔄",
		patternName: "analyze_claims",
		strategyName: "self-consistent",
		placeholder: "Cole o problema para análise com autoconsistência...",
		category: "avançado",
	},
	{
		id: "atomic_decompose",
		label: "Decompor Problema",
		description: "Atom of Thought: divida em subproblemas independentes.",
		icon: "⚛️",
		patternName: "create_design_document",
		strategyName: "aot",
		placeholder: "Descreva o problema complexo para decompor...",
		category: "avançado",
	},
];

export const WORKFLOW_CATEGORIES = [
	{ id: "geral",     label: "Geral",      icon: "🏠" },
	{ id: "código",    label: "Código",     icon: "💻" },
	{ id: "finanças",  label: "Finanças",   icon: "💰" },
	{ id: "conteúdo",  label: "Conteúdo",   icon: "📰" },
	{ id: "avançado",  label: "Avançado",   icon: "🧪" },
] as const;

export type WorkflowCategory = (typeof WORKFLOW_CATEGORIES)[number]["id"];

export function getWorkflow(id: string): Workflow | undefined {
	return WORKFLOWS.find((w) => w.id === id);
}

export function getWorkflowsByCategory(category: WorkflowCategory): Workflow[] {
	return WORKFLOWS.filter((w) => w.category === category);
}
