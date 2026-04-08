import { writable } from "svelte/store";
import type { Session, User } from "@supabase/supabase-js";
import { getSupabaseClient } from "$lib/services/SupabaseClient";

export const authUser = writable<User | null>(null);
export const authSession = writable<Session | null>(null);
export const authLoading = writable<boolean>(true);
export const authError = writable<string | null>(null);
export const authInitialized = writable<boolean>(false);

let _initialized = false;

export async function initAuth(): Promise<void> {
	if (_initialized) return;
	_initialized = true;
	authLoading.set(true);
	authError.set(null);

	const supabase = getSupabaseClient();
	if (!supabase) {
		authError.set(
			"Supabase não configurado. Defina VITE_SUPABASE_URL e VITE_SUPABASE_PUBLISHABLE_KEY.",
		);
		authLoading.set(false);
		authInitialized.set(true);
		return;
	}

	const { data, error } = await supabase.auth.getSession();
	if (error) {
		authError.set(error.message);
	}
	authSession.set(data.session ?? null);
	authUser.set(data.session?.user ?? null);
	authLoading.set(false);
	authInitialized.set(true);

	supabase.auth.onAuthStateChange((_event, session) => {
		authSession.set(session ?? null);
		authUser.set(session?.user ?? null);
		authLoading.set(false);
	});
}

export async function signInWithPassword(
	email: string,
	password: string,
): Promise<{ ok: boolean; error?: string }> {
	const supabase = getSupabaseClient();
	if (!supabase) return { ok: false, error: "Supabase não configurado." };

	const { error } = await supabase.auth.signInWithPassword({ email, password });
	if (error) return { ok: false, error: error.message };
	return { ok: true };
}

export async function sendMagicLink(
	email: string,
): Promise<{ ok: boolean; error?: string }> {
	const supabase = getSupabaseClient();
	if (!supabase) return { ok: false, error: "Supabase não configurado." };

	const { error } = await supabase.auth.signInWithOtp({
		email,
		options: {
			emailRedirectTo: typeof window !== "undefined" ? window.location.href : undefined,
		},
	});
	if (error) return { ok: false, error: error.message };
	return { ok: true };
}

export async function signOut(): Promise<void> {
	const supabase = getSupabaseClient();
	if (supabase) {
		await supabase.auth.signOut({ scope: "local" });
	}
	clearLocalSupabaseAuthStorage();
	authSession.set(null);
	authUser.set(null);
	authLoading.set(false);
}

/** Remove tokens Supabase guardados no navegador (útil se a sessão ficar presa). */
export function clearLocalSupabaseAuthStorage(): void {
	if (typeof window === "undefined") return;
	for (const storage of [localStorage, sessionStorage]) {
		for (const key of Object.keys(storage)) {
			if (key.startsWith("sb-")) storage.removeItem(key);
		}
	}
}

