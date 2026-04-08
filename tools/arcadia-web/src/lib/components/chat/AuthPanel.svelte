<script lang="ts">
	import { Button } from "$lib/components/ui/button";
	import {
		authError,
		authLoading,
		sendMagicLink,
		signInWithPassword,
	} from "$lib/store/auth-store";

	let email = "";
	let password = "";
	let submitting = false;
	let localMessage = "";

	async function handlePasswordLogin() {
		localMessage = "";
		submitting = true;
		const result = await signInWithPassword(email.trim(), password);
		submitting = false;
		if (!result.ok) {
			localMessage = result.error ?? "Falha no login.";
		}
	}

	async function handleMagicLink() {
		localMessage = "";
		submitting = true;
		const result = await sendMagicLink(email.trim());
		submitting = false;
		if (!result.ok) {
			localMessage = result.error ?? "Falha ao enviar magic link.";
			return;
		}
		localMessage = "Link mágico enviado. Verifique seu e-mail.";
	}
</script>

<div class="auth-wrap">
	<div class="auth-card">
		<h2>Entrar no ARCÁDIA OS</h2>
		<p class="sub">Use e-mail + senha ou magic link.</p>

		<div class="field">
			<label for="auth-email">E-mail</label>
			<input id="auth-email" type="email" bind:value={email} placeholder="voce@exemplo.com" />
		</div>

		<div class="field">
			<label for="auth-password">Senha</label>
			<input id="auth-password" type="password" bind:value={password} placeholder="••••••••" />
		</div>

		<div class="actions">
			<Button
				on:click={handlePasswordLogin}
				disabled={submitting || $authLoading || !email || !password}
			>
				{submitting ? "Entrando..." : "Entrar com senha"}
			</Button>

			<Button
				variant="ghost"
				on:click={handleMagicLink}
				disabled={submitting || $authLoading || !email}
			>
				Enviar magic link
			</Button>
		</div>

		{#if localMessage}
			<p class="msg">{localMessage}</p>
		{/if}
		{#if $authError}
			<p class="err">{$authError}</p>
		{/if}
	</div>
</div>

<style>
	.auth-wrap {
		min-height: 60vh;
		display: grid;
		place-items: center;
		padding: 1rem;
	}
	.auth-card {
		width: min(460px, 95vw);
		padding: 1rem;
		border-radius: 0.85rem;
		border: 1px solid rgba(255, 255, 255, 0.14);
		background: rgba(15, 23, 42, 0.6);
	}
	h2 {
		margin: 0;
		font-size: 1rem;
	}
	.sub {
		margin: 0.25rem 0 1rem;
		font-size: 0.8rem;
		opacity: 0.75;
	}
	.field {
		margin-bottom: 0.75rem;
	}
	label {
		display: block;
		font-size: 0.75rem;
		margin-bottom: 0.25rem;
		opacity: 0.85;
	}
	input {
		width: 100%;
		padding: 0.5rem 0.6rem;
		border-radius: 0.55rem;
		border: 1px solid rgba(255, 255, 255, 0.14);
		background: rgba(15, 23, 42, 0.35);
		color: #e2e8f0;
	}
	.actions {
		display: flex;
		gap: 0.5rem;
		margin-top: 0.5rem;
	}
	.msg {
		margin-top: 0.75rem;
		font-size: 0.78rem;
		color: #93c5fd;
	}
	.err {
		margin-top: 0.5rem;
		font-size: 0.78rem;
		color: #fca5a5;
	}
</style>

