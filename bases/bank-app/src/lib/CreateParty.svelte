<script>
  import { create_party } from "./api.mjs";
  import Modal from "./Modal.svelte";

  let { onCreated, showToast } = $props();

  let open = $state(false);
  let displayName = $state("Jane Doe");
  let givenName = $state("Jane");
  let middleNames = $state("");
  let familyName = $state("Doe");
  let dateOfBirth = $state("1990-01-15");
  let nationality = $state("GB");
  let niType = $state("NATIONAL_INSURANCE");
  let niValue = $state("TN000001A");
  let niCountry = $state("GBR");
  let submitting = $state(false);

  function errorDetail(body) {
    if (!body) return null;
    return body.message ?? body.error ?? body.detail
           ?? (typeof body === "string" ? body : JSON.stringify(body));
  }

  function dobToInt(dateStr) {
    return parseInt(dateStr.replace(/-/g, ""), 10);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    submitting = true;
    try {
      const res = await create_party({
        "display-name": displayName,
        "given-name": givenName,
        "middle-names": middleNames || undefined,
        "family-name": familyName,
        "date-of-birth": dobToInt(dateOfBirth),
        "nationality": nationality,
        "national-identifier": {
          "type": niType,
          "value": niValue,
          "issuing-country": niCountry,
        },
      });
      const status = res["http-status"];
      if (status >= 200 && status < 300) {
        open = false;
        showToast?.({ type: "success", message: "Party created" });
        onCreated?.();
      } else if (status >= 400 && status < 500) {
        showToast?.({ type: "warning", message: errorDetail(res.body) ?? `HTTP ${status}` });
      } else {
        showToast?.({ type: "error", message: errorDetail(res.body) ?? `HTTP ${status}` });
      }
    } catch (err) {
      showToast?.({ type: "error", message: err.message });
    } finally {
      submitting = false;
    }
  }
</script>

<button class="new-btn" onclick={() => open = true}>+ New Party</button>

<Modal {open} onClose={() => open = false} title="New Party">
  <form onsubmit={handleSubmit}>
    <label>
      Display Name
      <input type="text" bind:value={displayName} required />
    </label>

    <label>
      Given Name
      <input type="text" bind:value={givenName} required />
    </label>

    <label>
      Middle Names
      <input type="text" bind:value={middleNames} />
    </label>

    <label>
      Family Name
      <input type="text" bind:value={familyName} required />
    </label>

    <label>
      Date of Birth
      <input type="date" bind:value={dateOfBirth} required />
    </label>

    <label>
      Nationality
      <input type="text" bind:value={nationality} required maxlength="2"
             placeholder="e.g. GB" />
    </label>

    <fieldset>
      <legend>National Identifier</legend>

      <label>
        Type
        <select bind:value={niType} required>
          <option value="NATIONAL_INSURANCE">National Insurance</option>
        </select>
      </label>

      <label>
        Value
        <input type="text" bind:value={niValue} required
               placeholder="e.g. TN000001A" />
      </label>

      <label>
        Issuing Country
        <input type="text" bind:value={niCountry} required maxlength="3"
               placeholder="e.g. GBR" />
      </label>
    </fieldset>

    <button type="submit" disabled={submitting}>
      {submitting ? "Creating..." : "Create Party"}
    </button>
  </form>
</Modal>

<style>
  .new-btn {
    padding: 0.5rem 1rem;
    background: #16a34a;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .new-btn:hover {
    background: #15803d;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-weight: 500;
  }

  input, select {
    padding: 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 1rem;
    background: var(--bg-input);
    color: var(--text);
  }

  fieldset {
    border: 1px solid var(--border-input);
    border-radius: 4px;
    padding: 1rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  legend {
    font-weight: 600;
    padding: 0 0.5rem;
  }

  button[type="submit"] {
    padding: 0.6rem 1.2rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 1rem;
    cursor: pointer;
  }

  button[type="submit"]:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
</style>
