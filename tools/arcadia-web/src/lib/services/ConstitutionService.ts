import type { SupabaseClient } from "@supabase/supabase-js";
import { getSupabaseClient } from "$lib/services/SupabaseClient";
import {
	activeConstitution,
	constitutionError,
	constitutionLoading,
	type ConstitutionRecord,
} from "$lib/store/constitution-store";

export async function ensureUserProfile(userId: string, email?: string | null): Promise<void> {
	const supabase = getSupabaseClient();
	if (!supabase) return;

	await supabase.from("users").upsert(
		{
			id: userId,
			email: email ?? null,
		},
		{ onConflict: "id" },
	);
}

function buildConstitutionPrompt(c: ConstitutionRecord): string {
	const rules = JSON.stringify(c.rules ?? {}, null, 2);
	const principles = JSON.stringify(c.principles ?? [], null, 2);
	return [
		"[ARCÁDIA CONSTITUTION]",
		`Título: ${c.title} (v${c.version})`,
		`Estilo narrativo: ${c.narrative_style ?? "padrão"}`,
		"Princípios:",
		principles,
		"Regras:",
		rules,
		"Respeite esta constituição em toda decisão e recomendação.",
	].join("\n");
}

export function getConstitutionPromptFromStore(
	constitution: ConstitutionRecord | null,
): string {
	if (!constitution) return "";
	return buildConstitutionPrompt(constitution);
}

async function fetchActiveConstitutionRow(
	supabase: SupabaseClient,
	userId: string,
): Promise<ConstitutionRecord | null> {
	const { data, error } = await supabase
		.from("constitutions")
		.select(
			"id,title,version,principles,rules,narrative_style,metadata,is_active,created_at",
		)
		.eq("user_id", userId)
		.eq("is_active", true)
		.order("created_at", { ascending: false })
		.limit(1)
		.maybeSingle();

	if (error) throw error;
	if (!data) return null;

	return {
		id: data.id as string,
		title: data.title as string,
		version: data.version as number,
		principles: data.principles,
		rules: data.rules,
		narrative_style: (data.narrative_style as string | null) ?? null,
		metadata: data.metadata,
	};
}

export async function loadActiveConstitution(userId: string): Promise<void> {
	const supabase = getSupabaseClient();
	if (!supabase) return;
	constitutionLoading.set(true);
	constitutionError.set(null);

	try {
		let constitution = await fetchActiveConstitutionRow(supabase, userId);

		// If seed trigger has not executed yet for this user, call DB seed function once.
		if (!constitution) {
			await supabase.rpc("seed_default_arcadia_data", { target_user_id: userId });
			constitution = await fetchActiveConstitutionRow(supabase, userId);
		}

		activeConstitution.set(constitution);
	} catch (error) {
		constitutionError.set(error instanceof Error ? error.message : String(error));
		activeConstitution.set(null);
	} finally {
		constitutionLoading.set(false);
	}
}

