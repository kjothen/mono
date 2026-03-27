<script>
  import { list_cash_accounts, get_cash_account, close_cash_account, simulate_inbound_transfer, submit_internal_payment } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount, tick } from "svelte";

  function toDisplay(minorUnits) {
    const n = (minorUnits / 100).toFixed(2);
    const [whole, frac] = n.split(".");
    return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",") + "." + frac;
  }

  function onAmountKey(e) {
    // allow navigation, delete, backspace, tab
    if (["Backspace", "Delete", "ArrowLeft", "ArrowRight",
         "Tab", "Home", "End"].includes(e.key)) return;
    // allow one decimal point
    if (e.key === ".") {
      if (e.target.value.includes(".")) e.preventDefault();
      return;
    }
    // block everything except digits
    if (!/^\d$/.test(e.key)) e.preventDefault();
  }

  function formatAmount(e) {
    const input = e.target;
    let raw = input.value.replace(/[^0-9.]/g, "");
    const parts = raw.split(".");
    if (parts.length > 2)
      raw = parts[0] + "." + parts.slice(1).join("");
    if (parts.length === 2 && parts[1].length > 2)
      raw = parts[0] + "." + parts[1].slice(0, 2);
    const [whole, frac] = raw.split(".");
    const formatted =
      whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",")
      + (frac !== undefined ? "." + frac : "");
    const pos = input.selectionStart;
    const diff = formatted.length - input.value.length;
    input.value = formatted;
    input.setSelectionRange(pos + diff, pos + diff);
  }

  function inputToMinor(value) {
    const raw = value.replace(/,/g, "");
    return Math.round(parseFloat(raw || "0") * 100);
  }

  function inputValid(value) {
    const raw = value?.replace(/,/g, "");
    const n = parseFloat(raw || "0");
    return n >= 0.01;
  }

  let accounts = $state([]);
  let links = $state({});
  let loading = $state(false);
  let error = $state(null);
  let currentQuery = $state(null);
  let expandedAccountId = $state(null);
  let activeTab = $state({});
  let showFundDialog = $state(false);
  let fundAccountId = $state(null);
  let fundAmount = $state("1.00");
  let fundCurrency = $state("GBP");
  let fundSubmitting = $state(false);
  let fundError = $state(null);

  let showRewardDialog = $state(false);
  let rewardAccountId = $state(null);
  let rewardAmount = $state("0.50");
  let rewardCurrency = $state("GBP");
  let rewardSubmitting = $state(false);
  let rewardError = $state(null);

  let showPayInDialog = $state(false);
  let payInAccountId = $state(null);
  let payInDebtorId = $state(null);
  let payInAmount = $state("");
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
    fundAmount = "50,000.00";
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
      const res = await simulate_inbound_transfer(orgId, fundAccountId, inputToMinor(fundAmount), fundCurrency);
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        showFundDialog = false;
        await refreshAll();
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
    rewardAmount = "1,000.00";
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
      const res = await submit_internal_payment(debtorId, rewardAccountId, rewardCurrency, inputToMinor(rewardAmount), "Reward");
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        showRewardDialog = false;
        await refreshAll();
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
    payInAmount = "";
    payInCurrency = acct.currency ?? "GBP";
    payInError = null;
    showPayInDialog = true;
  }

  function closePayInDialog() {
    showPayInDialog = false;
  }

  function payInDebtorAvailable() {
    const sib = payInSiblings.find(s => s["account-id"] === payInDebtorId);
    return sib?.["available-balance"]?.value ?? 0;
  }

  function payInExceedsAvailable() {
    const amount = inputToMinor(payInAmount || "0");
    return amount > payInDebtorAvailable();
  }

  function payInRemainingOrAvailable() {
    const available = payInDebtorAvailable();
    const amount = inputToMinor(payInAmount || "0");
    if (amount === 0) return { value: available, label: "", exceeded: false };
    if (amount > available) return { value: available, label: "available", exceeded: true };
    return { value: available - amount, label: "remaining", exceeded: false };
  }

  async function submitPayIn() {
    payInSubmitting = true;
    payInError = null;
    try {
      const res = await submit_internal_payment(payInDebtorId, payInAccountId, payInCurrency, inputToMinor(payInAmount), "Pay In");
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        showPayInDialog = false;
        await refreshAll();
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
      await refreshAll();
    } finally {
      delete closing[accountId];
    }
  }

  async function refreshAccount(accountId) {
    const res = await get_cash_account(accountId);
    if (res["http-status"] >= 200 && res["http-status"] < 300) {
      const idx = accounts.findIndex(a => a["account-id"] === accountId);
      if (idx >= 0) accounts[idx] = res.body;
    }
  }

  async function refreshAll() {
    await load(currentQuery);
  }

  function toggleExpanded(accountId) {
    if (expandedAccountId === accountId) {
      expandedAccountId = null;
      return;
    }
    expandedAccountId = accountId;
    activeTab[accountId] = activeTab[accountId] ?? "balances";
  }

  function switchTab(accountId, tab) {
    activeTab[accountId] = tab;
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
        <th class="amount">Posted</th>
        <th class="amount">Available</th>
        <th>Created</th>
        <th>Updated</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if accounts.length === 0 && !loading}
        <tr><td colspan="11" class="empty">No accounts found</td></tr>
      {/if}
      {#each accounts as acct}
        <tr class="account-row" onclick={() => toggleExpanded(acct["account-id"])}>
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
          <td class="mono amount">
            {#if acct["posted-balance"]}
              {toDisplay(acct["posted-balance"].value)}
            {:else}
              —
            {/if}
          </td>
          <td class="mono amount">
            {#if acct["available-balance"]}
              {toDisplay(acct["available-balance"].value)}
            {:else}
              —
            {/if}
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
          <tr class="detail-row">
            <td colspan="11">
              <div class="tab-bar">
                <button class="tab-btn" class:active={activeTab[acct["account-id"]] === "balances"}
                        onclick={(e) => { e.stopPropagation(); switchTab(acct["account-id"], "balances"); }}>
                  Balances
                </button>
                <button class="tab-btn" class:active={activeTab[acct["account-id"]] === "transactions"}
                        onclick={(e) => { e.stopPropagation(); switchTab(acct["account-id"], "transactions"); }}>
                  Transactions
                </button>
              </div>

              {#if activeTab[acct["account-id"]] === "balances"}
                {#if (acct.balances ?? []).length === 0}
                  <div class="detail-empty">No balances found</div>
                {:else}
                  <table class="detail-table">
                    <thead>
                      <tr>
                        <th class="col-narrow">Balance Type</th>
                        <th class="col-narrow">Balance Status</th>
                        <th class="col-currency">Currency</th>
                        <th class="amount">Credit</th>
                        <th class="amount">Debit</th>
                        <th>Created</th>
                      </tr>
                    </thead>
                    <tbody>
                      {#each acct.balances ?? [] as bal}
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
                          <td class="mono amount">{toDisplay(bal.credit)}</td>
                          <td class="mono amount">{toDisplay(bal.debit)}</td>
                          <td title={bal["created-at"]}>{time_ago(bal["created-at"])}</td>
                        </tr>
                      {/each}
                    </tbody>
                  </table>
                {/if}
              {:else if activeTab[acct["account-id"]] === "transactions"}
                {#if (acct.transactions ?? []).length === 0}
                  <div class="detail-empty">No transactions found</div>
                {:else}
                  <table class="detail-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Type</th>
                        <th>Direction</th>
                        <th class="amount">Amount</th>
                        <th>Reference</th>
                        <th>Created</th>
                      </tr>
                    </thead>
                    <tbody>
                      {#each acct.transactions ?? [] as txn}
                        <tr>
                          <td class="mono">{txn["transaction-id"]}</td>
                          <td>{txn["transaction-type"]}</td>
                          <td>{txn.side}</td>
                          <td class="mono amount">{toDisplay(txn.amount)}</td>
                          <td>{txn.reference ?? ""}</td>
                          <td title={txn["created-at"]}>{time_ago(txn["created-at"])}</td>
                        </tr>
                      {/each}
                    </tbody>
                  </table>
                {/if}
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
          Amount
          <input type="text" inputmode="decimal" bind:value={fundAmount} onkeydown={onAmountKey} oninput={formatAmount} />
        </label>
        <label>
          Currency
          <input type="text" bind:value={fundCurrency} maxlength="3" />
        </label>
        <div class="dialog-actions">
          <button class="cancel-btn" onclick={closeFundDialog} disabled={fundSubmitting}>Cancel</button>
          <button class="submit-btn" onclick={submitFund} disabled={fundSubmitting || !inputValid(fundAmount)}>
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
          Amount
          <input type="text" inputmode="decimal" bind:value={rewardAmount} onkeydown={onAmountKey} oninput={formatAmount} />
        </label>
        <label>
          Currency
          <span class="currency-display">{rewardCurrency}</span>
        </label>
        <div class="dialog-actions">
          <button class="cancel-btn" onclick={closeRewardDialog} disabled={rewardSubmitting}>Cancel</button>
          <button class="submit-btn" onclick={submitReward} disabled={rewardSubmitting || !inputValid(rewardAmount)}>
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
        {#if true}
          {@const info = payInRemainingOrAvailable()}
          <span class="balance-info" class:exceeded={info.exceeded}>
            {payInCurrency} {toDisplay(info.value)}
            {#if info.label}
              <span class="balance-label">{info.label}</span>
            {/if}
          </span>
        {/if}
        <label>
          Amount
          <input type="text" inputmode="decimal" bind:value={payInAmount} onkeydown={onAmountKey} oninput={formatAmount} />
        </label>
        <label>
          Currency
          <span class="currency-display">{payInCurrency}</span>
        </label>
        <div class="dialog-actions">
          <button class="cancel-btn" onclick={closePayInDialog} disabled={payInSubmitting}>Cancel</button>
          <button class="submit-btn" onclick={submitPayIn} disabled={payInSubmitting || !inputValid(payInAmount) || payInExceedsAvailable()}>
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

  th.amount,
  td.amount {
    text-align: right;
  }

  .detail-table th.col-narrow {
    width: 17%;
    white-space: nowrap;
  }

  .detail-table th.col-currency {
    width: 1%;
    white-space: nowrap;
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

  .detail-row > td {
    padding: 0 0.6rem 0.6rem 2rem;
    background: var(--bg-secondary);
    border-bottom: 1px solid var(--border);
  }

  .tab-bar {
    display: flex;
    gap: 0.25rem;
    margin-bottom: 0.5rem;
    padding-top: 0.5rem;
  }

  .tab-btn {
    padding: 0.3rem 0.8rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    background: var(--bg);
    color: var(--text-muted);
    font-size: 0.8rem;
    cursor: pointer;
  }

  .tab-btn.active {
    background: var(--bg-secondary);
    color: var(--text);
    font-weight: 600;
    border-color: var(--text-muted);
  }

  .tab-btn:hover:not(.active) {
    background: var(--bg-hover);
  }

  .detail-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.82rem;
  }

  .detail-table th,
  .detail-table td {
    text-align: left;
    padding: 0.35rem 0.5rem;
    border-bottom: 1px solid var(--border);
  }

  .detail-table th {
    background: transparent;
    font-weight: 600;
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--text-muted);
  }

  .detail-loading,
  .detail-empty {
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

  .balance-info {
    display: block;
    padding: 0.25rem 0;
    font-size: 0.85rem;
    font-family: monospace;
    color: var(--text-muted);
  }

  .balance-info.exceeded {
    color: #dc2626;
  }

  .balance-label {
    font-family: sans-serif;
    font-size: 0.75rem;
    margin-left: 0.3rem;
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
