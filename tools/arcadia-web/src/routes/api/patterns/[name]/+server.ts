import { error, json } from "@sveltejs/kit";
import type { RequestHandler } from "@sveltejs/kit";
import { arcadiaBuiltinPatterns } from "$lib/server/arcadia-builtin-patterns";

export const GET: RequestHandler = async ({ params }) => {
	const name = params.name ?? "";
	const Pattern = arcadiaBuiltinPatterns[name];
	if (!Pattern) {
		throw error(404, `Pattern não encontrado: ${name}`);
	}
	return json({ Pattern });
};
