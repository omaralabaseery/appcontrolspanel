import { escape } from "../core/ui.js";
let state = { status: "checking", projectId: "", database: "(default)" };
let authenticated = false;
let pending = false;
const labels = {
  checking: "Checking Firestore…",
  connected: "Firestore connected",
  not_configured: "Firebase backend not configured",
  unavailable: "Firestore connection failed",
  backend_unavailable: "Backend unavailable",
};
export function connectionSummary() {
  const frontendProject = window.PANEL_CONFIG?.firebase?.projectId || "Not configured";
  const mismatch = state.projectId && state.projectId !== frontendProject;
  const color = state.status === "connected" && !mismatch ? "green" : "amber";
  let hint = state.status === "connected"
    ? "Server connection verified. Sign in as admin to load database records."
    : state.status === "not_configured"
      ? "Enable Firebase and configure server credentials, then restart the backend."
      : state.status === "checking"
        ? "Checking the server’s database connection."
        : "Check the backend, Firebase permissions and network, then retry.";
  if (authenticated) hint = "Admin signed in · workspace uses database records.";
  if (mismatch) hint = `Project mismatch: browser uses ${frontendProject}. Match the browser and server configuration.`;
  return `<div class="connection-copy"><div><span class="badge ${color}">${escape(labels[state.status] || labels.unavailable)}</span> <strong>Project: ${escape(state.projectId || frontendProject)}</strong> <span class="muted">· Firestore ${escape(state.database)}</span></div><small>${escape(hint)}</small><small>Data source: ${authenticated ? "Database records" : "Demo samples"} · Admin: ${authenticated ? "Signed in" : "Not signed in"}${state.checkedAt ? ` · Checked ${escape(new Date(state.checkedAt).toLocaleTimeString())}` : ""}</small></div><button type="button" class="button small" data-check-connection ${pending ? "disabled" : ""}>Check connection</button>`;
}
export function updateConnectionView(live = authenticated) {
  authenticated = live;
  document.querySelectorAll("#connection-status, [data-connection-summary]").forEach(el => {
    el.innerHTML = connectionSummary();
  });
}
export async function refreshConnection() {
  if (pending) return;
  pending = true;
  updateConnectionView();
  try {
    const response = await fetch((window.PANEL_CONFIG?.apiBase || "/api") + "/connection", {
      cache: "no-store", signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) throw Error("Backend status unavailable");
    const data = await response.json();
    if (!Object.hasOwn(labels, data.status) || typeof data.projectId !== "string") throw Error("Invalid connection status");
    state = data;
  } catch {
    state = { status: "backend_unavailable", projectId: "", database: "(default)" };
  } finally {
    pending = false;
    updateConnectionView();
  }
}
document.addEventListener("click", event => {
  if (event.target.closest("[data-check-connection]")) refreshConnection();
});
