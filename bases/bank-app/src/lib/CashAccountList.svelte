<script>
  import { list_cash_accounts, close_cash_account, list_balances, simulate_inbound_transfer, submit_internal_payment } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount, tick } from "svelte";

  let accounts = $state([]);
  let links = $state({});
  let loading = $state(false);
  let error = $state(null);
  let currentQuery = $state(null);
  let expandedAccountId = $state(null);
  let balances = $state({});
  let loadingBalances = $state({});
  let showFundDialog = $state(false);
  let fundAccountId = $state(null);
  let fundAmount = $state(100);
  let fundCurrency = $state("GBP");
  let fundSubmitting = $state(false);
  let fundError = $state(null);

  let showRewardDialog = $state(false);
  let rewardAccountId = $state(null);
  let rewardAmount = $state(50);
  let rewardCurrency = $state("GBP");
  let rewardSubmitting = $state(false);
  let rewardError = $state(null);

  let showPayInDialog = $state(false);
  let payInAccountId = $state(null);
  let payInDebtorId = $state(null);
  let payInAmount = $state(null);
  let payInCurrency = $state("GBP");
  let payInSubmitting = $state(false);
  let payInError = $state(null);
  let payInSiblings = $state([]);

  let { orgId } = $props();

  function customerAccounts() {
    return accounts.filter(a =>
      a["account-type"] !== "internal" &&
      a["account-type"] !== "settlement" &&
      a["account-status"] === "opened");
  }

  function siblingAccounts(acct) {
    return customerAccounts().filter(a =>
      a["party-id"] === acct["party-id"] &&
      a["account-id"] !== acct["account-id"]);
  }

  function hasPayInSiblings(acct) {
    return siblingAccounts(acct).length > 0;
  }

  function openFundDialog(acct) {
    fundAmount = 100;
    fundCurrency = acct.currency ?? "GBP";
    fundAccountId = acct["account-id"];
    fundError = null;
    showFundDialog = true;
  }

  function closeFundDialog() {
    showFundDialog = false;
  }

  async function submitFund() {
    fundSubmitting = true;
    fundError = null;
    try {
      const res = await simulate_inbound_transfer(orgId, fundAccountId, fundAmount, fundCurrency);
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        showFundDialog = false;
        load(currentQuery);
      } else {
        fundError = res.body?.detail ?? `HTTP ${res["http-status"]}`;
      }
    } catch (err) {
      fundError = err.message;
    } finally {
      fundSubmitting = false;
    }
  }

  function settlementAccountId() {
    const acct = accounts.find(a => a["account-type"] === "settlement");
    return acct?.["account-id"];
  }

  function openRewardDialog(acct) {
    rewardAmount = 50;
    rewardCurrency = acct.currency ?? "GBP";
    rewardAccountId = acct["account-id"];
    rewardError = null;
    showRewardDialog = true;
  }

  function closeRewardDialog() {
    showRewardDialog = false;
  }

  async function submitReward() {
    rewardSubmitting = true;
    rewardError = null;
    try {
      const debtorId = settlementAccountId();
      if (!debtorId) {
        rewardError = "No settlement account found";
        return;
      }
      const res = await submit_internal_payment(debtorId, rewardAccountId, rewardCurrency, rewardAmount, "Reward");
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        showRewardDialog = false;
        load(currentQuery);
      } else {
        rewardError = res.body?.detail ?? `HTTP ${res["http-status"]}`;
      }
    } catch (err) {
      rewardError = err.message;
    } finally {
      rewardSubmitting = false;
    }
  }

  function openPayInDialog(acct) {
    const siblings = siblingAccounts(acct);
    payInSiblings = siblings;
    payInDebtorId = siblings[0]?.["account-id"] ?? null;
    payInAccountId = acct["account-id"];
    payInAmount = null;
    payInCurrency = acct.currency ?? "GBP";
    payInError = null;
    showPayInDialog = true;
  }

  function closePayInDialog() {
    showPayInDialog = false;
  }

  async function submitPayIn() {
    payInSubmitting = true;
    payInError = null;
    try {
      const res = await submit_internal_payment(payInDebtorId, payInAccountId, payInCurrency, payInAmount, "Pay In");
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        showPayInDialog = false;
        load(currentQuery);
      } else {
        payInError = res.body?.detail ?? `HTTP ${res["http-status"]}`;
      }
    } catch (err) {
      payInError = err.message;
    } finally {
      payInSubmitting = false;
    }
  }

  function queryFromLink(url) {
    const idx = url.indexOf("?");
    return idx >= 0 ? url.substring(idx + 1) : null;
  }

  export async function load(queryString) {
    loading = true;
    error = null;
    currentQuery = queryString ?? null;
    try {
      const acctRes = await list_cash_accounts(queryString);
      if (acctRes["http-status"] >= 200 && acctRes["http-status"] < 300) {
        accounts = acctRes.body["cash-accounts"] ?? [];
        links = acctRes.body.links ?? {};
        balances = {};
      } else {
        error = acctRes.body?.error ?? `HTTP ${acctRes["http-status"]}`;
        accounts = [];
        links = {};
      }
    } catch (err) {
      error = err.message;
      accounts = [];
      links = {};
    } finally {
      loading = false;
    }
  }

  let closing = $state({});

  async function handleClose(accountId) {
    closing[accountId] = true;
    try {
      await close_cash_account(accountId);
      await load(currentQuery);
    } finally {
      delete closing[accountId];
    }
  }

  async function toggleBalances(accountId) {
    if (expandedAccountId === accountId) {
      expandedAccountId = null;
      return;
    }
    expandedAccountId = accountId;
    {
      loadingBalances[accountId] = true;
      try {
        const res = await list_balances(accountId);
        if (res["http-status"] >= 200 && res["http-status"] < 300) {
          balances[accountId] = res.body.balances ?? [];
        } else {
          balances[accountId] = [];
        }
      } catch (_) {
        balances[accountId] = [];
      } finally {
        delete loadingBalances[accountId];
      }
    }
  }

  function scanOf(acct) {
    const addr = (acct["payment-addresses"] ?? [])[0];
    const scan = addr?.identifier?.scan;
    if (!scan) return null;
    return `${scan["sort-code"]} ${scan["account-number"]}`;
  }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M2 4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1H2V4zm0 3v5a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H2zm3 2h2a1 1 0 0 1 0 2H5a1 1 0 0 1 0-2z"/></svg>
      Cash Accounts
    </h2>
    <button class="refresh" onclick={() => load(currentQuery)} disabled={loading}>
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
        <th>Name</th>
        <th>Type</th>
        <th>Currency</th>
        <th>SCAN</th>
        <th>Status</th>
        <th>Created</th>
        <th>Updated</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if accounts.length === 0 && !loading}
        <tr><td colspan="9" class="empty">No accounts found</td></tr>
      {/if}
      {#each accounts as acct}
        <tr class="account-row" onclick={() => toggleBalances(acct["account-id"])}>
          <td class="mono">
            <span class="chevron">{expandedAccountId === acct["account-id"] ? "\u25BC" : "\u25B6"}</span>
            {acct["account-id"]}
          </td>
          <td>{acct.name ?? ""}</td>
          <td>{acct["account-type"] ?? ""}</td>
          <td>{acct.currency}</td>
          <td class="mono">{scanOf(acct) ?? ""}</td>
          <td>
            <span class="status-badge"
                  class:opened={acct["account-status"] === "opened"}
                  class:opening={acct["account-status"] === "opening"}
                  class:closing={acct["account-status"] === "closing"}
                  class:closed={acct["account-status"] === "closed"}>
              {acct["account-status"]}
            </span>
          </td>
          <td title={acct["created-at"]}>{time_ago(acct["created-at"])}</td>
          <td title={acct["updated-at"]}>{time_ago(acct["updated-at"])}</td>
          <td>
            {#if acct["account-status"] === "opened" && acct["account-type"] === "settlement"}
              <button
                class="fund-btn"
                onclick={(e) => { e.stopPropagation(); openFundDialog(acct); }}
              >
                Fund
              </button>
            {:else if acct["account-status"] === "opened" && acct["account-type"] !== "internal" && acct["account-type"] !== "settlement"}
              {#if hasPayInSiblings(acct)}
                <button
                  class="payin-btn"
                  onclick={(e) => { e.stopPropagation(); openPayInDialog(acct); }}
                >
                  Pay In
                </button>
              {/if}
              <button
                class="reward-btn"
                onclick={(e) => { e.stopPropagation(); openRewardDialog(acct); }}
              >
                Reward
              </button>
              <button
                class="close-btn"
                disabled={closing[acct["account-id"]]}
                onclick={(e) => { e.stopPropagation(); handleClose(acct["account-id"]); }}
              >
                {closing[acct["account-id"]] ? "Closing..." : "Close"}
              </button>
            {/if}
          </td>
        </tr>
        {#if expandedAccountId === acct["account-id"]}
          <tr class="balances-row">
            <td colspan="9">
              {#if loadingBalances[acct["account-id"]]}
                <div class="balances-loading">Loading balances...</div>
              {:else if (balances[acct["account-id"]] ?? []).length === 0}
                <div class="balances-empty">No balances found</div>
              {:else}
                <table class="balances-table">
                  <thead>
                    <tr>
                      <th>Balance Type</th>
                      <th>Balance Status</th>
                      <th>Currency</th>
                      <th>Credit</th>
                      <th>Debit</th>
                      <th>Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {#each balances[acct["account-id"]] as bal}
                      <tr>
                        <td>{bal["balance-type"]}</td>
                        <td>
                          <span class="status-badge"
                                class:opened={bal["balance-status"] === "posted"}
                                class:opening={bal["balance-status"] === "pending-incoming" || bal["balance-status"] === "pending-outgoing"}
                                class:closed={bal["balance-status"] === "closed"}>
                            {bal["balance-status"]}
                          </span>
                        </td>
                        <td>{bal.currency}</td>
                        <td class="mono">{bal.credit}</td>
                        <td class="mono">{bal.debit}</td>
                        <td title={bal["created-at"]}>{time_ago(bal["created-at"])}</td>
                      </tr>
                    {/each}
                  </tbody>
                </table>
              {/if}
            </td>
          </tr>
        {/if}
      {/each}
    </tbody>
  </table>

  {#if showFundDialog}
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div class="dialog-overlay" onclick={closeFundDialog}>
      <div class="dialog" onclick={(e) => e.stopPropagation()}>
        <h3>Simulate Inbound Transfer</h3>
        <p class="dialog-sub">Fund settlement account <span class="mono">{fundAccountId}</span></p>
        {#if fundError}
          <div class="error-msg">{fundError}</div>
        {/if}
        <label>
          Amount (minor units)
          <input type="number" bind:value={fundAmount} min="1" />
        </label>
        <label>
          Currency
          <input type="text" bind:value={fundCurrency} maxlength="3" />
        </label>
        <div class="dialog-actions">
          <button class="cancel-btn" onclick={closeFundDialog} disabled={fundSubmitting}>Cancel</button>
          <button class="submit-btn" onclick={submitFund} disabled={fundSubmitting || fundAmount < 1}>
            {fundSubmitting ? "Submitting..." : "Submit"}
          </button>
        </div>
      </div>
    </div>
  {/if}

  {#if showRewardDialog}
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div class="dialog-overlay" onclick={closeRewardDialog}>
      <div class="dialog" onclick={(e) => e.stopPropagation()}>
        <h3>Reward</h3>
        <p class="dialog-sub">Transfer from settlement to <span class="mono">{rewardAccountId}</span></p>
        {#if rewardError}
          <div class="error-msg">{rewardError}</div>
        {/if}
        <label>
          Amount (minor units)
          <input type="number" bind:value={rewardAmount} min="1" />
        </label>
        <label>
          Currency
          <span class="currency-display">{rewardCurrency}</span>
        </label>
        <div class="dialog-actions">
          <button class="cancel-btn" onclick={closeRewardDialog} disabled={rewardSubmitting}>Cancel</button>
          <button class="submit-btn" onclick={submitReward} disabled={rewardSubmitting || rewardAmount < 1}>
            {rewardSubmitting ? "Submitting..." : "Submit"}
          </button>
        </div>
      </div>
    </div>
  {/if}

  {#if showPayInDialog}
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div class="dialog-overlay" onclick={closePayInDialog}>
      <div class="dialog" onclick={(e) => e.stopPropagation()}>
        <h3>Pay In</h3>
        <p class="dialog-sub">Transfer to <span class="mono">{payInAccountId}</span></p>
        {#if payInError}
          <div class="error-msg">{payInError}</div>
        {/if}
        <label>
          From account
          <select bind:value={payInDebtorId}>
            {#each payInSiblings as sib}
              <option value={sib["account-id"]}>{sib.name ?? sib["account-id"]}</option>
            {/each}
          </select>
        </label>
        <label>
          Amount (minor units)
          <input type="number" bind:value={payInAmount} min="1" />
        </label>
        <label>
          Currency
          <span class="currency-display">{payInCurrency}</span>
        </label>
        <div class="dialog-actions">
          <button class="cancel-btn" onclick={closePayInDialog} disabled={payInSubmitting}>Cancel</button>
          <button class="submit-btn" onclick={submitPayIn} disabled={payInSubmitting || !payInAmount || payInAmount < 1}>
            {payInSubmitting ? "Submitting..." : "Submit"}
          </button>
        </div>
      </div>
    </div>
  {/if}

  <div class="pagination">
    <button
      disabled={!links.prev || loading}
      onclick={() => load(queryFromLink(links.prev))}
    >
      Prev
    </button>
    <button
      disabled={!links.next || loading}
      onclick={() => load(queryFromLink(links.next))}
    >
      Next
    </button>
  </div>
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

  .pagination {
    display: flex;
    justify-content: center;
    gap: 0.5rem;
    margin-top: 1rem;
  }

  .pagination button {
    padding: 0.4rem 1rem;
    background: var(--bg-pagination);
    border: 1px solid var(--border-input);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.85rem;
  }

  .pagination button:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  .pagination button:not(:disabled):hover {
    background: var(--bg-hover);
  }

  .status-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
  }

  .status-badge.opened {
    background: #dcfce7;
    color: #166534;
  }

  .status-badge.opening {
    background: #fef9c3;
    color: #854d0e;
  }

  .status-badge.closing {
    background: #ffedd5;
    color: #9a3412;
  }

  .status-badge.closed {
    background: #fee2e2;
    color: #991b1b;
  }

  .close-btn {
    padding: 0.25rem 0.6rem;
    background: #dc2626;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
  }

  .close-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .close-btn:not(:disabled):hover {
    background: #b91c1c;
  }

  .account-row {
    cursor: pointer;
  }

  .account-row:hover {
    background: var(--bg-hover);
  }

  .chevron {
    display: inline-block;
    width: 1em;
    font-size: 0.7rem;
    color: var(--text-muted);
  }

  .balances-row > td {
    padding: 0 0.6rem 0.6rem 2rem;
    background: var(--bg-secondary);
    border-bottom: 1px solid var(--border);
  }

  .balances-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.82rem;
  }

  .balances-table th,
  .balances-table td {
    text-align: left;
    padding: 0.35rem 0.5rem;
    border-bottom: 1px solid var(--border);
  }

  .balances-table th {
    background: transparent;
    font-weight: 600;
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--text-muted);
  }

  .balances-loading,
  .balances-empty {
    padding: 0.75rem 0;
    color: var(--text-faint);
    font-size: 0.85rem;
  }

  .payin-btn {
    padding: 0.25rem 0.6rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
    margin-right: 0.25rem;
  }

  .payin-btn:hover {
    background: #1d4ed8;
  }

  .reward-btn {
    padding: 0.25rem 0.6rem;
    background: #16a34a;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
    margin-right: 0.25rem;
  }

  .reward-btn:hover {
    background: #15803d;
  }

  .currency-display {
    display: block;
    padding: 0.4rem 0.5rem;
    font-size: 0.9rem;
    color: var(--text-muted);
  }

  .fund-btn {
    padding: 0.25rem 0.6rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
  }

  .fund-btn:hover {
    background: #1d4ed8;
  }

  .dialog-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 100;
  }

  .dialog {
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1.5rem;
    width: 360px;
    max-width: 90vw;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
  }

  .dialog h3 {
    margin: 0 0 0.25rem;
  }

  .dialog-sub {
    margin: 0 0 1rem;
    font-size: 0.85rem;
    color: var(--text-muted);
  }

  .dialog label {
    display: block;
    margin-bottom: 0.75rem;
    font-size: 0.85rem;
    font-weight: 600;
  }

  .dialog input {
    display: block;
    width: 100%;
    margin-top: 0.25rem;
    padding: 0.4rem 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.9rem;
    background: var(--bg-input);
    color: var(--text);
    box-sizing: border-box;
  }

  .dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 0.5rem;
    margin-top: 1rem;
  }

  .cancel-btn {
    padding: 0.4rem 0.8rem;
    background: var(--bg-secondary);
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
    color: var(--text);
  }

  .submit-btn {
    padding: 0.4rem 0.8rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .submit-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .submit-btn:not(:disabled):hover {
    background: #1d4ed8;
  }
</style>
