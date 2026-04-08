import { createClient, type SupabaseClient } from "@supabase/supabase-js";

let _client: SupabaseClient | null = null;

function resolveKey(): string | undefined {
	return (
		import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY ||
		import.meta.env.VITE_SUPABASE_ANON_KEY
	);
}

export function getSupabaseClient(): SupabaseClient | null {
	if (_client) return _client;

	const url = import.meta.env.VITE_SUPABASE_URL;
	const key = resolveKey();

	if (!url || !key) return null;

	_client = createClient(url, key, {
		auth: {
			persistSession: true,
			autoRefreshToken: true,
		},
	});
	return _client;
}

