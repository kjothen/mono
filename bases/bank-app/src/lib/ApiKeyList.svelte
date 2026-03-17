<script>
  import { list_api_keys } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount } from "svelte";

  let apiKeys = $state([]);
  let loading = $state(false);
  let error = $state(null);

  export async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_api_keys();
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        apiKeys = res.body["api-keys"] ?? [];
      } else {
        error = res.body?.error ?? `HTTP ${res["http-status"]}`;
        apiKeys = [];
      }
    } catch (err) {
      error = err.message;
      apiKeys = [];
    } finally {
      loading = false;
    }
  }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M10.5 1a4.5 4.5 0 0 0-4.38 5.57L2 10.7V14a1 1 0 0 0 1 1h2v-2h2v-2h1.59l.53-.53A4.5 4.5 0 1 0 10.5 1zm1 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z"/></svg>
      API Keys
    </h2>
    <button class="refresh" onclick={() => load()} disabled={loading}>
      {loading ? "Loading..." : "Refresh"}
    </button>
  </div>

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  <table>
    <thead>
      <tr>
        <th>ID</th>
        <th>Key Prefix</th>
        <th>Name</th>
        <th>Created</th>
      </tr>
    </thead>
    <tbody>
      {#if apiKeys.length === 0 && !loading}
        <tr><td colspan="4" class="empty">No API keys found</td></tr>
      {/if}
      {#each apiKeys as key}
        <tr>
          <td class="mono">{key.id ?? ""}</td>
          <td class="mono">{key["key-prefix"]}</td>
          <td>{key.name}</td>
          <td title={key["created-at"]}>{time_ago(key["created-at"])}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</section>

<style>
  section {
    margin-top: 2rem;
  }

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
  }

  h2 {
    margin: 0;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .refresh {
    padding: 0.4rem 0.8rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .refresh:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .error-msg {
    background: var(--bg-error);
    border: 1px solid var(--border-error);
    padding: 0.75rem;
    border-radius: 4px;
    margin-bottom: 1rem;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.9rem;
  }

  th, td {
    text-align: left;
    padding: 0.5rem 0.6rem;
    border-bottom: 1px solid var(--border);
  }

  th {
    background: var(--bg-secondary);
    font-weight: 600;
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--text-muted);
  }

  .mono {
    font-family: monospace;
    font-size: 0.8rem;
  }

  .empty {
    text-align: center;
    color: var(--text-faint);
    padding: 1.5rem;
  }
</style>
