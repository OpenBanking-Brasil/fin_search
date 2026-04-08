const BASE = () =>
	(typeof window !== "undefined"
		? (window as unknown as Record<string, string>).__FABRIC_BASE_URL__
		: undefined) ??
	import.meta.env.VITE_FABRIC_BASE_URL ??
	"http://127.0.0.1:18080";

export interface PatternDetail {
	name: string;
	pattern: string;
	description?: string;
}

async function req<T>(path: string, options?: RequestInit): Promise<T> {
	const res = await fetch(`${BASE()}${path}`, {
		headers: { "Content-Type": "application/json" },
		...options,
	});
	if (!res.ok) {
		const text = await res.text().catch(() => "");
		throw new Error(`Fabric API ${path} → ${res.status}: ${text}`);
	}
	return res.json() as Promise<T>;
}

export async function getPattern(name: string): Promise<PatternDetail> {
	return req<PatternDetail>(`/api/patterns/${encodeURIComponent(name)}`);
}

export async function createPattern(name: string, content: string): Promise<void> {
	await req<unknown>("/api/patterns", {
		method: "POST",
		body: JSON.stringify({ name, pattern: content }),
	});
}

export async function updatePattern(name: string, content: string): Promise<void> {
	await req<unknown>(`/api/patterns/${encodeURIComponent(name)}`, {
		method: "PUT",
		body: JSON.stringify({ name, pattern: content }),
	});
}

export async function deletePattern(name: string): Promise<void> {
	await req<unknown>(`/api/patterns/${encodeURIComponent(name)}`, {
		method: "DELETE",
	});
}
