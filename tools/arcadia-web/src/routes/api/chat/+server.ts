import { GoogleGenerativeAI } from "@google/generative-ai";
import { env } from "$env/dynamic/private";
import type { RequestHandler } from "@sveltejs/kit";
import type { ChatRequest, StreamResponse } from "$lib/interfaces/chat-interface";
function resolveModelId(raw: string | undefined): string {
	const d = (raw ?? "").trim();
	if (!d) return "gemini-2.0-flash";
	if (d.includes("|")) return d.split("|").pop()!.trim();
	if (d.includes("/")) return d.split("/").pop()!.trim();
	return d;
}

function encodeSse(obj: StreamResponse): Uint8Array {
	const line = `data: ${JSON.stringify(obj)}\n\n`;
	return new TextEncoder().encode(line);
}

export const POST: RequestHandler = async ({ request }) => {
	const apiKey = env.ARCADIA_GEMINI_API_KEY || env.GEMINI_API_KEY;
	if (!apiKey) {
		return new Response(
			JSON.stringify({
				error:
					"ARCÁDIA: defina ARCADIA_GEMINI_API_KEY ou GEMINI_API_KEY no .env (servidor).",
			}),
			{ status: 503, headers: { "Content-Type": "application/json" } },
		);
	}

	let body: ChatRequest;
	try {
		body = (await request.json()) as ChatRequest;
	} catch {
		return new Response(JSON.stringify({ error: "Invalid JSON body" }), {
			status: 400,
			headers: { "Content-Type": "application/json" },
		});
	}

	const prompt = body.prompts?.[0];
	if (!prompt) {
		return new Response(JSON.stringify({ error: "Missing prompts[0]" }), {
			status: 400,
			headers: { "Content-Type": "application/json" },
		});
	}

	const systemPrompt = prompt.systemPrompt ?? "You are a helpful assistant.";
	const userText = prompt.userInput ?? "";
	const modelId = resolveModelId(prompt.model);

	const genAI = new GoogleGenerativeAI(apiKey);
	const model = genAI.getGenerativeModel({
		model: modelId,
		systemInstruction: systemPrompt,
		generationConfig: {
			temperature: body.temperature ?? 0.7,
			topP: body.top_p ?? 0.9,
		},
	});

	const stream = new ReadableStream<Uint8Array>({
		async start(controller) {
			try {
				const result = await model.generateContentStream(userText);
				for await (const chunk of result.stream) {
					const text = chunk.text();
					if (text) {
						const chunkResponse: StreamResponse = {
							type: "content",
							format: "markdown",
							content: text,
						};
						controller.enqueue(encodeSse(chunkResponse));
					}
				}
			} catch (e) {
				const msg = e instanceof Error ? e.message : String(e);
				controller.enqueue(
					encodeSse({
						type: "error",
						format: "plain",
						content: msg,
					}),
				);
			} finally {
				controller.close();
			}
		},
	});

	return new Response(stream, {
		headers: {
			"Content-Type": "text/event-stream; charset=utf-8",
			"Cache-Control": "no-cache",
			Connection: "keep-alive",
		},
	});
};
