const BASE = () =>
	(typeof window !== "undefined"
		? (window as unknown as Record<string, string>).__FABRIC_BASE_URL__
		: undefined) ??
	import.meta.env.VITE_FABRIC_BASE_URL ??
	"http://127.0.0.1:18080";

export interface FabricSession {
	name: string;
}

export interface SessionMessage {
	role: "user" | "assistant" | "system";
	content: string;
}

export interface SessionDetail {
	name: string;
	messages: SessionMessage[];
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

export async function listSessions(): Promise<FabricSession[]> {
	const data = await req<{ sessions?: FabricSession[] } | FabricSession[]>(
		"/api/sessions",
	);
	return Array.isArray(data) ? data : (data.sessions ?? []);
}

export async function getSession(name: string): Promise<SessionDetail> {
	return req<SessionDetail>(`/api/sessions/${encodeURIComponent(name)}`);
}

export async function createSession(name: string): Promise<void> {
	await req<unknown>("/api/sessions", {
		method: "POST",
		body: JSON.stringify({ name }),
	});
}

export async function deleteSession(name: string): Promise<void> {
	await req<unknown>(`/api/sessions/${encodeURIComponent(name)}`, {
		method: "DELETE",
	});
}
