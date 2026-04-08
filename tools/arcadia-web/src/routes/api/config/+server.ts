import { json } from "@sveltejs/kit";
import type { RequestHandler } from "@sveltejs/kit";
import type { ModelConfig } from "$lib/interfaces/model-interface";

const DEFAULT: ModelConfig = {
	model: "gemini-2.0-flash",
	temperature: 0.7,
	top_p: 0.9,
	maxLength: 2000,
	frequency: 0.5,
	presence: 0,
};

export const GET: RequestHandler = async () => {
	return json(DEFAULT);
};
