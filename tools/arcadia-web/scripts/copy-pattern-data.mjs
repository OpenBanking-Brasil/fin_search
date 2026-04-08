/**
 * Copia pattern_descriptions.json para static/data (Windows/macOS/Linux).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.join(__dirname, "..");
const src = path.join(
	__dirname,
	"pattern_descriptions",
	"pattern_descriptions.json",
);
const destDir = path.join(webRoot, "static", "data");
const dest = path.join(destDir, "pattern_descriptions.json");

fs.mkdirSync(destDir, { recursive: true });
if (fs.existsSync(src)) {
	fs.copyFileSync(src, dest);
} else {
	console.warn(
		"[copy-pattern-data] Aviso: ficheiro em falta:",
		src,
		"(padrões embutidos no código podem bastar)",
	);
}
