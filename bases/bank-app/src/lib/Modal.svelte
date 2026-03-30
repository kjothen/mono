<script>
  let { open = false, onClose, title, children } = $props();
  let dialogEl = $state();

  $effect(() => {
    if (!dialogEl) return;
    if (open && !dialogEl.open) {
      dialogEl.showModal();
    } else if (!open && dialogEl.open) {
      dialogEl.close();
    }
  });

  function handleClick(e) {
    if (e.target === dialogEl) {
      onClose?.();
    }
  }

  function handleCancel(e) {
    e.preventDefault();
    onClose?.();
  }
</script>

<dialog bind:this={dialogEl} onclick={handleClick} oncancel={handleCancel}>
  <div class="modal-content">
    <div class="modal-header">
      <h3>{title}</h3>
      <button class="modal-close" onclick={() => onClose?.()}>&times;</button>
    </div>
    <div class="modal-body">
      {@render children()}
    </div>
  </div>
</dialog>

<style>
  dialog {
    border: none;
    border-radius: 8px;
    padding: 0;
    max-width: 520px;
    width: 90vw;
    background: var(--bg);
    color: var(--text);
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.2);
  }

  dialog::backdrop {
    background: rgba(0, 0, 0, 0.5);
  }

  .modal-content {
    padding: 1.5rem;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1.25rem;
  }

  .modal-header h3 {
    margin: 0;
    font-size: 1.1rem;
  }

  .modal-close {
    background: none;
    border: none;
    width: 1.75rem;
    height: 1.75rem;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.2rem;
    cursor: pointer;
    color: var(--text-muted);
    border-radius: 4px;
    padding: 0;
    line-height: 1;
  }

  .modal-close:hover {
    color: var(--text);
    background: var(--bg-hover);
  }

  .modal-body {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }
</style>
