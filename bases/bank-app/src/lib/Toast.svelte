<script>
  let toasts = $state([]);
  let nextId = 0;

  export function show({ type = "success", message }) {
    const id = nextId++;
    toasts.push({ id, type, message });
    if (type === "success") {
      setTimeout(() => dismiss(id), 2000);
    }
  }

  function dismiss(id) {
    toasts = toasts.filter(t => t.id !== id);
  }
</script>

{#if toasts.length > 0}
  <div class="toast-container">
    {#each toasts as toast (toast.id)}
      <div class="toast" class:success={toast.type === "success"}
           class:error={toast.type === "error"}
           class:warning={toast.type === "warning"}>
        <span class="toast-msg">{toast.message}</span>
        {#if toast.type !== "success"}
          <button class="toast-dismiss" onclick={() => dismiss(toast.id)}>&times;</button>
        {/if}
      </div>
    {/each}
  </div>
{/if}

<style>
  .toast-container {
    position: fixed;
    top: 1rem;
    right: 1rem;
    z-index: 1000;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    max-width: 400px;
  }

  .toast {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.75rem 1rem;
    border-radius: 6px;
    font-family: system-ui, -apple-system, sans-serif;
    font-size: 0.9rem;
    font-weight: 600;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    animation: slide-in 0.2s ease-out;
  }

  @keyframes slide-in {
    from { transform: translateX(100%); opacity: 0; }
    to { transform: translateX(0); opacity: 1; }
  }

  .toast.success {
    background: #dcfce7;
    border: 1px solid #86efac;
    color: #166534;
  }

  .toast.error {
    background: #fee2e2;
    border: 1px solid #fca5a5;
    color: #991b1b;
  }

  .toast.warning {
    background: #fef9c3;
    border: 1px solid #fde047;
    color: #854d0e;
  }

  .toast-msg {
    flex: 1;
  }

  .toast-dismiss {
    background: none;
    border: none;
    font-size: 1.2rem;
    cursor: pointer;
    color: inherit;
    padding: 0;
    line-height: 1;
    opacity: 0.7;
  }

  .toast-dismiss:hover {
    opacity: 1;
  }
</style>
