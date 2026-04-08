import { json } from "@sveltejs/kit";
import { env } from "$env/dynamic/private";
import type { RequestHandler } from "@sveltejs/kit";

/** Saúde da API ARCÁDIA (rotas SvelteKit + Gemini no servidor). */
export const GET: RequestHandler = async () => {
	const geminiConfigured = !!(env.ARCADIA_GEMINI_API_KEY || env.GEMINI_API_KEY);
	return json({
		ok: true,
		backend: "arcadia-gemini",
		geminiConfigured,
	});
};
