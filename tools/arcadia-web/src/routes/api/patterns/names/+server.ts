import { json } from "@sveltejs/kit";
import type { RequestHandler } from "@sveltejs/kit";
import { ARCADIA_BUILTIN_PATTERN_NAMES } from "$lib/server/arcadia-builtin-patterns";

export const GET: RequestHandler = async () => {
	return json([...ARCADIA_BUILTIN_PATTERN_NAMES]);
};
