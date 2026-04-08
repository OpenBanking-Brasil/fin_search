import { writable } from "svelte/store";

export interface ConstitutionRecord {
	id: string;
	title: string;
	version: number;
	principles: unknown;
	rules: unknown;
	narrative_style: string | null;
	metadata: unknown;
}

export const activeConstitution = writable<ConstitutionRecord | null>(null);
export const constitutionLoading = writable<boolean>(false);
export const constitutionError = writable<string | null>(null);

