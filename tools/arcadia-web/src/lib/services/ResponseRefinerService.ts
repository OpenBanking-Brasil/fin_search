import { get } from "svelte/store";
import { patterns } from "$lib/store/pattern-store";

const REFINE_PATTERNS = [
	"improve_writing",
	"improve_prompt",
	"summarize",
];

const MIN_LENGTH_FOR_REFINE = 200;
const CONFIDENCE_THRESHOLD = 0.65;

export interface RefineResult {
	refined: string;
	patternUsed: string;
	skipped: boolean;
	reason: string;
}

function pickRefinePattern(): string | null {
	const available = get(patterns).map((p) => p.Name);
	for (const candidate of REFINE_PATTERNS) {
		if (available.includes(candidate)) return candidate;
	}
	return null;
}

function needsRefinement(output: string, confidence: number): boolean {
	if (output.trim().length < MIN_LENGTH_FOR_REFINE) return false;
	if (confidence >= 0.85) return false;
	return true;
}

export async function refine(
	originalOutput: string,
	userGoal: string,
	confidence: number,
	chatService: {
		streamWithPattern: (
			input: string,
			pattern: string,
			system?: string,
		) => Promise<ReadableStream<{ content: string; type: string }>>;
		processStream: (
			s: ReadableStream<{ content: string; type: string }>,
			onContent: (c: string) => void,
			onError: (e: Error) => void,
		) => Promise<void>;
	},
): Promise<RefineResult> {
	if (!needsRefinement(originalOutput, confidence)) {
		return {
			refined: originalOutput,
			patternUsed: "",
			skipped: true,
			reason:
				confidence >= 0.85
					? "alta confiança, refino desnecessário"
					: "resposta muito curta para refinar",
		};
	}

	const refinePattern = pickRefinePattern();
	if (!refinePattern) {
		return {
			refined: originalOutput,
			patternUsed: "",
			skipped: true,
			reason: "nenhum pattern de refino disponível",
		};
	}

	const patternData = get(patterns).find((p) => p.Name === refinePattern);
	const systemPrompt = patternData?.Pattern ?? "";

	const refinedInput = `Objetivo original do usuário: ${userGoal}\n\nTexto a melhorar:\n${originalOutput}`;

	let refinedOutput = "";
	try {
		const stream = await chatService.streamWithPattern(
			refinedInput,
			refinePattern,
			systemPrompt,
		);
		await chatService.processStream(
			stream,
			(c) => {
				refinedOutput += c;
			},
			() => {},
		);
	} catch {
		return {
			refined: originalOutput,
			patternUsed: refinePattern,
			skipped: true,
			reason: "erro durante refino, mantendo resposta original",
		};
	}

	if (!refinedOutput.trim()) {
		return {
			refined: originalOutput,
			patternUsed: refinePattern,
			skipped: true,
			reason: "refino retornou vazio, mantendo original",
		};
	}

	return {
		refined: refinedOutput,
		patternUsed: refinePattern,
		skipped: false,
		reason: `refinado com pattern "${refinePattern}"`,
	};
}
