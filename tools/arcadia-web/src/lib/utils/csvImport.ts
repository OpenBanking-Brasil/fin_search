/**
 * CSV simples para movimentos: data, valor, descrição, categoria (opcional), conta (opcional).
 * Formatos de data: ISO ou DD/MM/AAAA.
 */

export interface ParsedMovementRow {
	occurredAt: Date;
	amount: number;
	description: string;
	category: string | null;
	accountName: string | null;
}

function parseBrDate(s: string): Date | null {
	const t = s.trim();
	const m = /^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{2,4})$/.exec(t);
	if (!m) return null;
	const d = Number(m[1]);
	const mo = Number(m[2]) - 1;
	let y = Number(m[3]);
	if (y < 100) y += 2000;
	const dt = new Date(y, mo, d);
	return Number.isNaN(dt.getTime()) ? null : dt;
}

function parseAmount(raw: string): number | null {
	const s = raw.trim().replace(/\s/g, "").replace(",", ".");
	const n = Number(s);
	return Number.isFinite(n) ? n : null;
}

/** Primeira linha pode ser cabeçalho (data,valor,...) — deteta automaticamente. */
export function parseMovementsCsv(text: string): {
	rows: ParsedMovementRow[];
	errors: string[];
} {
	const errors: string[] = [];
	const lines = text
		.split(/\r?\n/)
		.map((l) => l.trim())
		.filter(Boolean);
	if (lines.length === 0) {
		return { rows: [], errors: ["Ficheiro vazio."] };
	}

	let start = 0;
	const first = lines[0].toLowerCase();
	if (
		first.includes("data") &&
		(first.includes("valor") || first.includes("amount"))
	) {
		start = 1;
	}

	const rows: ParsedMovementRow[] = [];

	for (let i = start; i < lines.length; i++) {
		const line = lines[i];
		const parts = line.split(/[;,]/).map((p) => p.trim());
		if (parts.length < 2) {
			errors.push(`Linha ${i + 1}: colunas insuficientes.`);
			continue;
		}

		const dateRaw = parts[0];
		const amountRaw = parts[1];
		const desc = parts[2] ?? "Importado";
		const cat = parts[3]?.length ? parts[3] : null;
		const acc = parts[4]?.length ? parts[4] : null;

		let occurred: Date | null = new Date(dateRaw);
		if (Number.isNaN(occurred.getTime())) {
			occurred = parseBrDate(dateRaw);
		}
		if (!occurred) {
			errors.push(`Linha ${i + 1}: data inválida "${dateRaw}".`);
			continue;
		}

		const amount = parseAmount(amountRaw);
		if (amount === null) {
			errors.push(`Linha ${i + 1}: valor inválido "${amountRaw}".`);
			continue;
		}

		rows.push({
			occurredAt: occurred,
			amount,
			description: desc,
			category: cat,
			accountName: acc,
		});
	}

	return { rows, errors };
}
