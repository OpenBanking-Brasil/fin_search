const BASE = () =>
	(typeof window !== "undefined"
		? (window as unknown as Record<string, string>).__FABRIC_BASE_URL__
		: undefined) ??
	import.meta.env.VITE_FABRIC_BASE_URL ??
	"http://127.0.0.1:18080";

export interface FabricContext {
	name: string;
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

export async function listContexts(): Promise<FabricContext[]> {
	const data = await req<{ contexts?: FabricContext[] } | FabricContext[]>(
		"/api/contexts",
	);
	return Array.isArray(data) ? data : (data.contexts ?? []);
}

export async function getContext(name: string): Promise<string> {
	const data = await req<{ content?: string; text?: string } | string>(
		`/api/contexts/${encodeURIComponent(name)}`,
	);
	if (typeof data === "string") return data;
	return data.content ?? data.text ?? "";
}

export async function saveContext(name: string, content: string): Promise<void> {
	// Try PUT first (update), fall back to POST (create)
	try {
		await req<unknown>(`/api/contexts/${encodeURIComponent(name)}`, {
			method: "PUT",
			body: JSON.stringify({ name, content }),
		});
	} catch {
		await req<unknown>("/api/contexts", {
			method: "POST",
			body: JSON.stringify({ name, content }),
		});
	}
}

export async function deleteContext(name: string): Promise<void> {
	await req<unknown>(`/api/contexts/${encodeURIComponent(name)}`, {
		method: "DELETE",
	});
}
