import { json } from "@sveltejs/kit";
import { env } from "$env/dynamic/private";
import type { RequestHandler } from "@sveltejs/kit";
import { createClient } from "@supabase/supabase-js";

/**
 * Ingestão server-to-server de movimentos (ex.: n8n, backend Open Finance).
 * Requer no .env do servidor: SUPABASE_SERVICE_ROLE_KEY (ou ARCADIA_SUPABASE_SERVICE_ROLE)
 * e ARCADIA_FINANCE_INGEST_SECRET.
 *
 * POST /api/finance/ingest
 * Headers: Authorization: Bearer <ARCADIA_FINANCE_INGEST_SECRET>
 * Body: { "userId": "<uuid>", "movements": [{ "occurred_at": "ISO", "amount": number, "description"?: string, "category"?: string, "account_id"?: uuid }] }
 */
export const POST: RequestHandler = async ({ request }) => {
	const secret = env.ARCADIA_FINANCE_INGEST_SECRET;
	const url = env.SUPABASE_URL ?? env.VITE_SUPABASE_URL;
	const serviceKey =
		env.SUPABASE_SERVICE_ROLE_KEY ?? env.ARCADIA_SUPABASE_SERVICE_ROLE;

	if (!secret || !url || !serviceKey) {
		return json(
			{ error: "Ingest não configurado no servidor." },
			{ status: 503 },
		);
	}

	const auth = request.headers.get("authorization");
	if (auth !== `Bearer ${secret}`) {
		return json({ error: "Não autorizado." }, { status: 401 });
	}

	let body: {
		userId?: string;
		movements?: Array<{
			occurred_at: string;
			amount: number;
			description?: string;
			category?: string;
			account_id?: string | null;
		}>;
	};
	try {
		body = (await request.json()) as typeof body;
	} catch {
		return json({ error: "JSON inválido." }, { status: 400 });
	}

	const userId = body.userId?.trim();
	if (!userId || !body.movements?.length) {
		return json({ error: "userId e movements obrigatórios." }, { status: 400 });
	}

	const admin = createClient(url, serviceKey, {
		auth: { persistSession: false, autoRefreshToken: false },
	});

	const rows = body.movements.map((m) => ({
		user_id: userId,
		account_id: m.account_id ?? null,
		occurred_at: m.occurred_at,
		amount: m.amount,
		description: m.description ?? null,
		category: m.category ?? null,
		source: "api" as const,
	}));

	const { data, error } = await admin.from("cash_movements").insert(rows).select("id");

	if (error) {
		return json({ error: error.message }, { status: 400 });
	}

	return json({ ok: true, inserted: data?.length ?? rows.length });
};
