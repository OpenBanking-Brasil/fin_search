import { createPipeline, transformers } from "pdf-to-markdown-core/lib/src";
import { PARSE_SCHEMA } from "pdf-to-markdown-core/lib/src/PdfParser";

// pdfjs-dist v5+ requires browser APIs at import time, so we use dynamic imports
let pdfjs: typeof import("pdfjs-dist") | null = null;

export class PdfConversionService {
	private async ensureInitialized(): Promise<typeof import("pdfjs-dist")> {
		if (!pdfjs) {
			// Dynamic import to avoid SSR issues with pdfjs-dist v5+
			pdfjs = await import("pdfjs-dist");
			const pdfConfig = (await import("./pdf-config")).default;
			console.log("PDF.js version:", pdfjs.version);
			await pdfConfig.initialize();
			console.log("Worker configuration complete");
		}
		return pdfjs;
	}

	async convertToMarkdown(file: File): Promise<string> {
		console.log("Starting PDF conversion:", {
			fileName: file.name,
			fileSize: file.size,
		});

		const pdfjsLib = await this.ensureInitialized();

		const buffer = await file.arrayBuffer();
		console.log("Buffer created:", buffer.byteLength);

		const pipeline = createPipeline(pdfjsLib, {
			transformConfig: {
				transformers,
			},
		});
		console.log("Pipeline created");

		const result = await pipeline.parse(buffer, (progress) =>
			console.log("Processing:", {
				stage: progress.stages,
				details: progress.stageDetails,
				progress: progress.stageProgress,
			}),
		);
		console.log("Parse complete, validating result");

		const transformed = result.transform();
		console.log("Transform applied:", transformed);

		const markdown = transformed.convert({
			convert: (items) => {
				console.log("PDF Structure:", {
					itemCount: items.length,
					firstItem: items[0],
					schema: PARSE_SCHEMA, // ['transform', 'width', 'height', 'str', 'fontName', 'dir']
				});

				const text = items
					.map((item) => item.value("str")) // Using 'str' instead of 'text' based on PARSE_SCHEMA
					.filter(Boolean)
					.join("\n");

				console.log("Converted text:", {
					length: text.length,
					preview: text.substring(0, 100),
				});

				return text;
			},
		});

		return markdown;
	}
}
