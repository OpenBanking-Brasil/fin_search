import type { SupabaseClient } from "@supabase/supabase-js";
import { getSupabaseClient } from "$lib/services/SupabaseClient";

export interface DecisionPersistenceInput {
	userInput: string;
	assistantOutput: string;
	sessionTitle?: string | null;
	route: {
		patternName: string;
		strategyName?: string;
		reason: string;
		confidence: number;
	};
	routerDurationMs: number;
	fallbackUsed: boolean;
}

interface SessionRow {
	id: string;
}

function normalizeSessionTitle(sessionTitle?: string | null): string {
	const value = (sessionTitle ?? "").trim();
	return value.length ? value : "ARCADIA Autopilot Session";
}

export class ParliamentPersistenceService {
	public async persistDecision(input: DecisionPersistenceInput): Promise<void> {
		const supabase = getSupabaseClient();
		if (!supabase) return;

		const {
			data: { user },
			error: userError,
		} = await supabase.auth.getUser();
		if (userError || !user) return;

		const sessionId = await this.getOrCreateSession(
			supabase,
			user.id,
			normalizeSessionTitle(input.sessionTitle),
			input,
		);
		if (!sessionId) return;

		const decisionId = await this.insertDecisionHistory(
			supabase,
			user.id,
			sessionId,
			input,
		);
		if (!decisionId) return;

		await this.insertPerformanceMetrics(supabase, user.id, sessionId, decisionId, input);
	}

	private async getOrCreateSession(
		supabase: SupabaseClient,
		userId: string,
		title: string,
		input: DecisionPersistenceInput,
	): Promise<string | null> {
		const nowIso = new Date().toISOString();
		const context = {
			origin: "arcadia_web_autopilot",
			pattern: input.route.patternName,
			strategy: input.route.strategyName ?? "",
			reason: input.route.reason,
			updatedAt: nowIso,
		};

		const { data: existing, error: existingError } = await supabase
			.from("sessions")
			.select("id")
			.eq("user_id", userId)
			.eq("title", title)
			.order("last_activity_at", { ascending: false })
			.limit(1);
		if (existingError) return null;

		const current = (existing as SessionRow[] | null)?.[0];
		if (current?.id) {
			const { error: updateError } = await supabase
				.from("sessions")
				.update({
					last_activity_at: nowIso,
					context,
					autopilot_enabled: true,
				})
				.eq("id", current.id);
			return updateError ? null : current.id;
		}

		const { data: inserted, error: insertError } = await supabase
			.from("sessions")
			.insert({
				user_id: userId,
				title,
				context,
				autopilot_enabled: true,
				started_at: nowIso,
				last_activity_at: nowIso,
			})
			.select("id")
			.single();

		if (insertError) return null;
		return (inserted as SessionRow).id;
	}

	private async insertDecisionHistory(
		supabase: SupabaseClient,
		userId: string,
		sessionId: string,
		input: DecisionPersistenceInput,
	): Promise<string | null> {
		const confidencePct = Math.round((input.route.confidence ?? 0) * 100);
		const nowIso = new Date().toISOString();

		const { data, error } = await supabase
			.from("decision_history")
			.insert({
				user_id: userId,
				session_id: sessionId,
				decision_type: "autopilot_route",
				input_context: {
					userInput: input.userInput,
					reason: input.route.reason,
					routerDurationMs: input.routerDurationMs,
				},
				options_evaluated: [],
				chosen_option: {
					patternName: input.route.patternName,
					strategyName: input.route.strategyName ?? "",
				},
				parliament_votes: [
					{
						agent: "Parlamento ARCÁDIA — Finanças Pessoais",
						vote: input.route.patternName,
						confidence: confidencePct,
					},
				],
				confidence: confidencePct,
				expected_value: null,
				actual_value: null,
				status: "executed",
				executed_at: nowIso,
			})
			.select("id")
			.single();

		if (error || !data) return null;
		return (data as { id: string }).id;
	}

	private async insertPerformanceMetrics(
		supabase: SupabaseClient,
		userId: string,
		sessionId: string,
		decisionId: string,
		input: DecisionPersistenceInput,
	): Promise<void> {
		const confidencePct = Math.round((input.route.confidence ?? 0) * 100);
		const nowIso = new Date().toISOString();
		const responseLength = input.assistantOutput.trim().length;
		const riskFlagValue = input.fallbackUsed ? 1 : 0;

		await supabase.from("performance_metrics").insert([
			{
				user_id: userId,
				session_id: sessionId,
				decision_id: decisionId,
				metric_name: "autopilot_confidence",
				metric_scope: "system",
				metric_value: confidencePct,
				baseline_value: 50,
				delta_value: confidencePct - 50,
				unit: "percent",
				window_start: nowIso,
				window_end: nowIso,
				metadata: {
					patternName: input.route.patternName,
					strategyName: input.route.strategyName ?? "",
				},
			},
			{
				user_id: userId,
				session_id: sessionId,
				decision_id: decisionId,
				metric_name: "router_latency_ms",
				metric_scope: "system",
				metric_value: input.routerDurationMs,
				baseline_value: 1000,
				delta_value: input.routerDurationMs - 1000,
				unit: "ms",
				window_start: nowIso,
				window_end: nowIso,
				metadata: {
					reason: input.route.reason,
				},
			},
			{
				user_id: userId,
				session_id: sessionId,
				decision_id: decisionId,
				metric_name: "assistant_response_length",
				metric_scope: "productivity",
				metric_value: responseLength,
				baseline_value: 800,
				delta_value: responseLength - 800,
				unit: "chars",
				window_start: nowIso,
				window_end: nowIso,
				metadata: {
					fallbackUsed: input.fallbackUsed,
				},
			},
			{
				user_id: userId,
				session_id: sessionId,
				decision_id: decisionId,
				metric_name: "fallback_risk_flag",
				metric_scope: "risk",
				metric_value: riskFlagValue,
				baseline_value: 0,
				delta_value: riskFlagValue,
				unit: "binary",
				window_start: nowIso,
				window_end: nowIso,
				metadata: {
					trigger: "route_confidence_low",
				},
			},
		]);
	}
}

