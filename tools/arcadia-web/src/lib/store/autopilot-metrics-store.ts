import { writable, derived } from "svelte/store";
import { browser } from "$app/environment";

export interface AutopilotEvent {
	timestamp: number;
	patternName: string;
	strategyName: string;
	confidence: number;
	wasRefined: boolean;
	durationMs: number;
	fallback: boolean;
	error: boolean;
}

const STORAGE_KEY = "fabric_autopilot_metrics";

function loadFromStorage(): AutopilotEvent[] {
	if (!browser) return [];
	try {
		return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "[]");
	} catch {
		return [];
	}
}

export const events = writable<AutopilotEvent[]>(loadFromStorage());

if (browser) {
	events.subscribe(($events) => {
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify($events.slice(-200)));
		} catch {}
	});
}

export function recordEvent(event: Omit<AutopilotEvent, "timestamp">) {
	events.update((list) => [
		...list,
		{ ...event, timestamp: Date.now() },
	]);
}

export function clearMetrics() {
	events.set([]);
}

export const summary = derived(events, ($events) => {
	const total = $events.length;
	if (total === 0) {
		return {
			total: 0,
			successRate: 0,
			fallbackRate: 0,
			errorRate: 0,
			refinedRate: 0,
			avgDurationMs: 0,
			avgConfidence: 0,
		};
	}

	const fallbacks = $events.filter((e) => e.fallback).length;
	const errors = $events.filter((e) => e.error).length;
	const refined = $events.filter((e) => e.wasRefined).length;
	const successes = $events.filter((e) => !e.error && !e.fallback).length;
	const avgDuration =
		$events.reduce((acc, e) => acc + e.durationMs, 0) / total;
	const avgConfidence =
		$events.reduce((acc, e) => acc + e.confidence, 0) / total;

	return {
		total,
		successRate: Math.round((successes / total) * 100),
		fallbackRate: Math.round((fallbacks / total) * 100),
		errorRate: Math.round((errors / total) * 100),
		refinedRate: Math.round((refined / total) * 100),
		avgDurationMs: Math.round(avgDuration),
		avgConfidence: Math.round(avgConfidence * 100) / 100,
	};
});
