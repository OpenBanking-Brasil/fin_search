import { purgeCss } from 'vite-plugin-tailwind-purgecss';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

/** ARCÁDIA OS: API em rotas SvelteKit (/api/*), sem proxy para servidor Fabric. */
export default defineConfig({
	plugins: [sveltekit(), purgeCss()],
	optimizeDeps: {
		include: ['pdfjs-dist'],
		esbuildOptions: {
			target: 'esnext',
			supported: {
				'top-level-await': true,
			},
		},
	},
	define: {
		'process.env': {
			NODE_ENV: JSON.stringify(process.env.NODE_ENV),
		},
		'process.platform': JSON.stringify(process.platform),
		'process.cwd': JSON.stringify('/'),
		'process.browser': true,
		process: {
			cwd: () => '/',
		},
	},
	resolve: {
		alias: {
			process: 'process/browser',
		},
	},
	server: {
		fs: {
			allow: ['..'],
		},
		watch: {
			usePolling: true,
			interval: 100,
			ignored: ['**/node_modules/**', '**/dist/**', '**/.git/**', '**/.svelte-kit/**'],
		},
	},
	build: {
		commonjsOptions: {
			transformMixedEsModules: true,
		},
		target: 'esnext',
		minify: true,
		rollupOptions: {
			output: {
				format: 'es',
			},
		},
	},
});
