/**
 * Métricas e insights para o Arcádia Dashboard (Finanças Pessoais High-End).
 */

export type MovementRow = {
	amount: number;
	occurred_at: string;
	category: string | null;
	description: string | null;
};

export function aggregateByCategory(
	rows: MovementRow[],
	since: Date,
): { name: string; total: number }[] {
	const map = new Map<string, number>();
	for (const r of rows) {
		const d = new Date(r.occurred_at);
		if (d < since) continue;
		const key = (r.category ?? "Outros").trim() || "Outros";
		map.set(key, (map.get(key) ?? 0) + Number(r.amount));
	}
	return [...map.entries()]
		.map(([name, total]) => ({ name, total }))
		.sort((a, b) => Math.abs(b.total) - Math.abs(a.total));
}

export function sumSince(rows: MovementRow[], since: Date): number {
	let s = 0;
	for (const r of rows) {
		if (new Date(r.occurred_at) >= since) s += Number(r.amount);
	}
	return s;
}

export function sumDay(rows: MovementRow[], day: Date): number {
	const start = new Date(day);
	start.setHours(0, 0, 0, 0);
	const end = new Date(day);
	end.setHours(23, 59, 59, 999);
	let s = 0;
	for (const r of rows) {
		const t = new Date(r.occurred_at);
		if (t >= start && t <= end) s += Number(r.amount);
	}
	return s;
}

/** Score 0–100 heurístico (não é aconselhamento profissional). */
export function computeFinancialHealthScore(input: {
	emergencyProgress: number; // 0–1 (atual vs meta)
	budgetUsedRatio: number; // 0–1+ (gasto vs orçamento mensal)
	negativeDaysRatio: number; // 0–1 fração de dias no mês com saldo líquido negativo
}): number {
	const e = Math.min(1, Math.max(0, input.emergencyProgress)) * 35;
	const b = Math.max(0, 1 - Math.min(1.5, input.budgetUsedRatio)) * 35;
	const f = (1 - Math.min(1, input.negativeDaysRatio)) * 30;
	return Math.round(Math.min(100, Math.max(0, e + b + f)));
}

export function buildDailyInsight(input: {
	categoryTotals: { name: string; total: number }[];
	deliveryRollingAvg: number;
	deliveryThisWeek: number;
}): { title: string; body: string; action?: string } {
	const delivery = input.categoryTotals.find((c) =>
		/delivery|ifood|uber\s*eats|rappi/i.test(c.name),
	);
	if (delivery && input.deliveryRollingAvg > 0) {
		const pct =
			((input.deliveryThisWeek - input.deliveryRollingAvg) /
				input.deliveryRollingAvg) *
			100;
		if (pct > 15) {
			return {
				title: "Insight do dia",
				body: `Está a gastar cerca de ${Math.round(Math.max(0, pct))}% acima da média em delivery esta semana.`,
				action: "Ativar modo Disciplina nas regras da Constituição (pausa 24h).",
			};
		}
	}
	return {
		title: "Insight do dia",
		body: "Continue a registar entradas e saídas para o Arcádia refinar alertas e o score de saúde financeira.",
		action: "Abrir Planeamento e rever o orçamento 50/30/20.",
	};
}
