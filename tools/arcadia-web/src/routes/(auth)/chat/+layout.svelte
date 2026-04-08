<script>
  import { disableScrollHandling } from "$app/navigation";
  import { onMount } from "svelte";

  onMount(() => {
    disableScrollHandling();
  });
</script>

<div class="chat-route-shell">
  <div class="chat-route-frame">
    <slot />
  </div>
</div>

<style>
  .chat-route-shell {
    min-height: calc(100vh - 8rem);
    width: 100%;
    display: flex;
    align-items: stretch;
    justify-content: center;
    padding: 0.5rem 0;
  }

  .chat-route-frame {
    width: min(1500px, 100%);
    border-radius: 1rem;
    border: 1px solid rgba(255, 255, 255, 0.08);
    background: linear-gradient(
      160deg,
      rgba(17, 24, 39, 0.55),
      rgba(15, 23, 42, 0.75)
    );
    box-shadow:
      0 20px 70px rgba(0, 0, 0, 0.35),
      inset 0 1px 0 rgba(255, 255, 255, 0.04);
    overflow: hidden;
  }

  @media (max-width: 1024px) {
    .chat-route-shell {
      padding: 0;
    }
    .chat-route-frame {
      border-radius: 0;
      border-left: 0;
      border-right: 0;
    }
  }
</style>

