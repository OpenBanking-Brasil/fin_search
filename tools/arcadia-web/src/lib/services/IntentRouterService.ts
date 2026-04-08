import { get } from "svelte/store";
import { patterns } from "$lib/store/pattern-store";
import { strategies } from "$lib/store/strategy-store";
import type { ChatService } from "$lib/services/ChatService";

/** Adapter real usado pelo IntentRouter (evita incompatibilidade de tipos com ChatService). */
type RouteChatAdapter = Pick<ChatService, "streamWithPattern" | "processStream">;

export interface RouteResult {
	patternName: string;
	strategyName: string;
	reason: string;
	confidence: number;
}

interface PatternRule {
	pattern: string;
	test: RegExp;
	strategy?: string;
	confidence: number;
	reason: string;
}

// ── Cache LRU ────────────────────────────────────────────────────────────────
const CACHE_MAX = 60;
const _cache = new Map<string, RouteResult>();

function _cacheKey(input: string): string {
	return input.trim().toLowerCase().slice(0, 150);
}
function _cacheGet(key: string): RouteResult | undefined {
	const v = _cache.get(key);
	if (v) { _cache.delete(key); _cache.set(key, v); }
	return v;
}
function _cacheSet(key: string, v: RouteResult) {
	if (_cache.size >= CACHE_MAX) _cache.delete(_cache.keys().next().value!);
	_cache.set(key, v);
}

// ── Mapeamento completo de estratégias disponíveis ────────────────────────────
// Nomes alinhados ao motor de estratégias (cot, cod, tot, aot, ltm, etc.).

const RULES: PatternRule[] = [
	// ── Código ──────────────────────────────────────────────────────────────
	{
		pattern: "explain_code",
		test: /c[oó]digo|programa[cç][aã]o|fun[cç][aã]o|bug|exception|stack.?trace|typescript|python|java\b|javascript|svelte|react|sql|kotlin|go\b|rust\b|c\+\+|c#/i,
		strategy: "cot",
		confidence: 0.92,
		reason: "explicação ou análise de código",
	},
	{
		pattern: "review_code",
		test: /review|revisar c[oó]digo|code.?review|feedback.*c[oó]digo|melhorar c[oó]digo|refatorar|refactor/i,
		strategy: "self-refine",
		confidence: 0.90,
		reason: "revisão de código",
	},
	// ── Resumo / Síntese ────────────────────────────────────────────────────
	{
		pattern: "summarize",
		test: /resum|sumari|sintetiz|breve|curto|r[aá]pido|tl;?dr|em poucas palavras/i,
		strategy: "standard",
		confidence: 0.87,
		reason: "sumarização de conteúdo",
	},
	{
		pattern: "create_summary",
		test: /relat[oó]rio|ata\b|reuni[aã]o|apresenta[cç][aã]o|slides|executive.?summary/i,
		strategy: "standard",
		confidence: 0.82,
		reason: "criação de resumo estruturado",
	},
	// ── Insights / Sabedoria ─────────────────────────────────────────────────
	{
		pattern: "extract_wisdom",
		test: /extrai|insight|sabedoria|wisdom|aprendizado|li[cç][aã]o|principais ideias|takeaway/i,
		strategy: "cot",
		confidence: 0.88,
		reason: "extração de insights e sabedoria",
	},
	// ── Comparação ───────────────────────────────────────────────────────────
	{
		pattern: "compare_and_contrast",
		test: /compar|versus|vs\b|diferen[cç]a|vantagem|desvantagem|alternativa|trade.?off|qual é melhor|pro[s ].*con/i,
		strategy: "tot",
		confidence: 0.86,
		reason: "comparação entre alternativas",
	},
	// ── Planejamento / Documentação ──────────────────────────────────────────
	{
		pattern: "create_design_document",
		test: /plano|roadmap|estrat[eé]gia|arquitetura|design doc|projeto|planejamento|como implementar|como criar/i,
		strategy: "cot",
		confidence: 0.82,
		reason: "planejamento e criação de documentos",
	},
	{
		pattern: "create_user_story",
		test: /user.?stor|história de usu[aá]rio|requisito|backlog|feature|funcionalidade|como usu[aá]rio/i,
		strategy: "standard",
		confidence: 0.84,
		reason: "criação de user stories",
	},
	// ── Escrita / Texto ───────────────────────────────────────────────────────
	{
		pattern: "improve_writing",
		test: /melhora|reescreve|refina|corrige|otimiza|revis[aã]|melhor[ae] o texto|polish|deixa mais claro/i,
		strategy: "self-refine",
		confidence: 0.85,
		reason: "melhoria e refinamento de texto",
	},
	{
		pattern: "write_essay",
		test: /essay|reda[cç][aã]o|ensaio|texto persuasivo|argumento.*texto|disserta/i,
		strategy: "cot",
		confidence: 0.81,
		reason: "escrita de ensaios e redações",
	},
	// ── Análise / Verificação ─────────────────────────────────────────────────
	{
		pattern: "analyze_claims",
		test: /analisa|verifica|fact.?check|afirma[cç][aã]o|argumento|valida|é verdade|isso é real/i,
		strategy: "cot",
		confidence: 0.84,
		reason: "análise e verificação de afirmações",
	},
	{
		pattern: "find_logical_fallacies",
		test: /fal[aá][cs]ia|l[oó]gica|argumento inv[aá]lido|contradit[oó]rio|contradição|erro de racioc[ií]nio/i,
		strategy: "cot",
		confidence: 0.82,
		reason: "detecção de falácias lógicas",
	},
	{
		pattern: "analyze_paper",
		test: /artigo|paper\b|pesquisa\b|estudo\b|cient[ií]fico|acadêmico|academico|abstract|metodologia/i,
		strategy: "cot",
		confidence: 0.87,
		reason: "análise de artigos acadêmicos",
	},
	// ── Tradução ──────────────────────────────────────────────────────────────
	{
		pattern: "translate",
		test: /traduz|tradu[çc][aã]o|translate|em ingl[eê]s|em portugu[eê]s|em espanhol|en français|auf deutsch/i,
		strategy: "standard",
		confidence: 0.92,
		reason: "tradução de conteúdo",
	},
	// ── Finanças / Open Finance ───────────────────────────────────────────────
	{
		pattern: "analyze_claims",
		test: /juros|taxa|investimento|ren[dt]abilidade|risco|carteira|ativo|passivo|fluxo de caixa|saldo|d[eé]bito|cr[eé]dito|parcela|empr[eé]stimo|financiamento|poupan[cç]a|tesouro direto|cdi|selic|ipca/i,
		strategy: "cot",
		confidence: 0.85,
		reason: "análise financeira",
	},
	// ── Explicação ────────────────────────────────────────────────────────────
	{
		pattern: "explain_code",
		test: /o que [eé]\b|explica\b|como funciona|como usar|para que serve|o que significa|defini[cç][aã]o de/i,
		strategy: "cot",
		confidence: 0.76,
		reason: "pedido de explicação",
	},
	// ── YouTube / Vídeo ───────────────────────────────────────────────────────
	{
		pattern: "extract_wisdom",
		test: /youtube\.com|youtu\.be|v[ií]deo|podcast|epis[oó]dio|aula|palestra|talk\b/i,
		strategy: "cot",
		confidence: 0.83,
		reason: "extração de conteúdo de vídeo/podcast",
	},
	// ── Email ─────────────────────────────────────────────────────────────────
	{
		pattern: "summarize",
		test: /email|e-mail|cabe[cç]alho|header|mensagem recebida|inbox/i,
		strategy: "standard",
		confidence: 0.80,
		reason: "análise ou resumo de email",
	},
];

async function suggestViaAI(
	input: string,
	chatService: RouteChatAdapter,
): Promise<string | null> {
	try {
		const available = get(patterns);
		const suggestPattern = available.find((p) => p.Name === "suggest_pattern");
		if (!suggestPattern?.Pattern) return null;

		let output = "";
		const stream = await chatService.streamWithPattern(
			input,
			"suggest_pattern",
			suggestPattern.Pattern,
		);

		await chatService.processStream(
			stream,
			(c) => {
				output += c;
			},
			() => {},
		);

		const availableNames = available
			.filter((p) => p.Name !== "suggest_pattern")
			.map((p) => p.Name.toLowerCase());

		const normalized = output.toLowerCase();
		const ranked = availableNames
			.map((name) => ({ name, idx: normalized.indexOf(name) }))
			.filter((x) => x.idx >= 0)
			.sort((a, b) => a.idx - b.idx);

		return ranked[0]?.name ?? null;
	} catch {
		return null;
	}
}

function resolveStrategy(strategyName: string): string {
	const available = get(strategies).map((s) => s.name.toLowerCase());
	if (!strategyName) return "";
	if (available.includes(strategyName.toLowerCase())) return strategyName;
	if (available.includes("standard")) return "standard";
	return "";
}

function patternExists(name: string): boolean {
	const available = get(patterns).map((p) => p.Name.toLowerCase());
	return available.includes(name.toLowerCase());
}

export async function route(
	input: string,
	chatService: RouteChatAdapter,
): Promise<RouteResult> {
	const key = _cacheKey(input);

	// Step 0: cache hit
	const cached = _cacheGet(key);
	if (cached) return cached;

	let result: RouteResult;

	// Step 1: heuristic pass — ordered by specificity
	const heuristic = RULES.find((r) => r.test.test(input) && patternExists(r.pattern));
	if (heuristic) {
		result = {
			patternName: heuristic.pattern,
			strategyName: resolveStrategy(heuristic.strategy ?? ""),
			reason: heuristic.reason,
			confidence: heuristic.confidence,
		};
		_cacheSet(key, result);
		return result;
	}

	// Step 2: AI suggest_pattern
	const aiSuggested = await suggestViaAI(input, chatService);
	if (aiSuggested && patternExists(aiSuggested)) {
		result = {
			patternName: aiSuggested,
			strategyName: resolveStrategy("standard"),
			reason: `sugestão automática: ${aiSuggested}`,
			confidence: 0.65,
		};
		_cacheSet(key, result);
		return result;
	}

	// Step 3: safe fallback
	result = {
		patternName: "summarize",
		strategyName: resolveStrategy("standard"),
		reason: "padrão seguro (sem correspondência)",
		confidence: 0.40,
	};
	_cacheSet(key, result);
	return result;
}

/** Exporta as estratégias canônicas para uso em selects de UI */
export const STRATEGY_OPTIONS = [
	{ value: "cot",            label: "Chain of Thought",    description: "Raciocínio passo a passo" },
	{ value: "cod",            label: "Chain of Draft",       description: "Elaboração iterativa (≤5 palavras/passo)" },
	{ value: "tot",            label: "Tree of Thought",      description: "Múltiplos caminhos, seleciona o melhor" },
	{ value: "aot",            label: "Atom of Thought",      description: "Divide em subproblemas atômicos" },
	{ value: "ltm",            label: "Least-to-Most",        description: "Resolve do mais fácil ao mais difícil" },
	{ value: "self-consistent",label: "Self-Consistent",      description: "Consenso entre múltiplos raciocínios" },
	{ value: "self-refine",    label: "Self-Refine",          description: "Responde, critica e refina" },
	{ value: "reflexion",      label: "Reflexion",            description: "Responde, critica brevemente, refina" },
	{ value: "standard",       label: "Standard",             description: "Resposta direta sem estratégia extra" },
] as const;

export type StrategyValue = (typeof STRATEGY_OPTIONS)[number]["value"];

/** Limpa o cache LRU (útil ao mudar de modelo/provider) */
export function clearRouteCache(): void {
	_cache.clear();
}
