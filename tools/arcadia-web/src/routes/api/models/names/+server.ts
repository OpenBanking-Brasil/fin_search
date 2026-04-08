import { json } from "@sveltejs/kit";
import type { RequestHandler } from "@sveltejs/kit";
import type { ModelsResponse } from "$lib/interfaces/model-interface";

const FALLBACK: ModelsResponse = {
	models: ["gemini-2.0-flash"],
	vendors: {
		Google: ["gemini-2.0-flash", "gemini-2.5-flash-preview-05-20"],
	},
};

/** Modelos disponíveis via Gemini (API no servidor). */
export const GET: RequestHandler = async () => {
	return json(FALLBACK);
};
