const $ = (s, root = document) => root.querySelector(s),
  $$ = (s, root = document) => [...root.querySelectorAll(s)];
const escape = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const icons = {
  dashboard: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  users:
    "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.9 M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4",
  activity: "M2 12h4l3-8 6 16 3-8h4",
  layers: "m12 3 10 5-10 5L2 8z M2 12l10 5 10-5 M2 16l10 5 10-5",
  tool: "M14 6a5 5 0 0 0-6 6L3 17a3 3 0 0 0 4 4l5-5a5 5 0 0 0 6-6l-4 2-2-2z",
  code: "m8 6-6 6 6 6 m8-12 6 6-6 6 m-3-15-2 18",
  download: "M12 3v12 m-5-5 5 5 5-5 M4 16v5h16v-5",
  phone:
    "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2 M10 18h4",
  refresh: "M20 7V2l-3 3a8 8 0 1 0 3 12 M20 7h-5",
  search: "M10 3a7 7 0 1 0 0 14 7 7 0 1 0 0-14 M15 15l6 6",
  arrow: "M5 12h14 m-5-5 5 5-5 5",
  shield: "m12 2 8 3v7c0 5-8 10-8 10S4 17 4 12V5z m-4 10 3 3 5-6",
  check: "m5 12 4 4L19 6",
  clock: "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20 M12 6v6l4 2",
};
const icon = (name) =>
  `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="${icons[name] || icons.dashboard}"/></svg>`;
const num = (n) => Number(n || 0).toLocaleString("en-US");
const date = (n) =>
  new Date(n).toLocaleString("en-GB", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
const initials = (n) =>
  String(n)
    .split(" ")
    .map((x) => x[0])
    .slice(0, 2)
    .join("");
const online = (u) => u.lastSeen > Date.now() - 300000;
const badge = (text, color = "") =>
  `<span class="badge ${color}">${escape(text)}</span>`;
function toast(text) {
  $("#toast").textContent = text;
  $("#toast").classList.add("visible");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => $("#toast").classList.remove("visible"), 4500);
}
function dialog(title, html) {
  $("#dialog-content").innerHTML =
    `<div class="dialog-header"><h2>${escape(title)}</h2><button class="icon-button" id="close-dialog" aria-label="Close dialog">✕</button></div><div class="dialog-body">${html}</div>`;
  $("#close-dialog").onclick = () => $("#dialog").close();
  if (!$("#dialog").open) $("#dialog").showModal();
}

export {
  $,
  $$,
  escape,
  icon,
  num,
  date,
  initials,
  online,
  badge,
  toast,
  dialog,
};
