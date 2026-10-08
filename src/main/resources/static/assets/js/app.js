import { connectionSummary, refreshConnection, updateConnectionView } from "./components/connection-status.js";
import { chart } from "./components/activity-chart.js";
import {
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
} from "./core/ui.js";
import { api, live, signIn, signOut } from "./services/api.js";
$$("[data-icon]").forEach((el) => (el.innerHTML = icon(el.dataset.icon)));
const titles = {
  overview: "Overview",
  users: "Users & devices",
  notifications: "Push notifications",
  logs: "Requests & logs",
  versions: "Version control",
  maintenance: "Maintenance",
  integration: "Integration",
};
const subtitles = {
  overview: "Monitor your application, users, and everyday operations.",
  users: "Explore user activity, linked devices, and application requests.",
  notifications: "Compose and send targeted messages to your mobile users.",
  logs: "Trace application requests and investigate reported problems.",
  versions: "Manage supported builds and guide users to the latest release.",
  maintenance: "Control application availability with localized messages.",
  integration: "Connect Firebase and integrate your mobile application.",
};
let page = "overview",
  generation = 0,
  users = [],
  events = [],
  policy,
  notifications = [],
  userCursor = "",
  eventCursor = "",
  noticeCursor = "";
$("#dialog").addEventListener("click", (e) => {
  if (e.target === $("#dialog")) $("#dialog").close();
});
$("#menu-toggle").onclick = () => $("#sidebar").classList.toggle("open");
function head(actions = "") {
  return `<div class="page-heading"><div><h1>${titles[page]}</h1><p class="subtitle">${subtitles[page]}</p></div><div class="row">${actions}</div></div>`;
}
function mode() {
  updateConnectionView(live);
  $("#mode-badge").textContent = live ? "Live Firebase" : "Demo mode";
  $("#mode-badge").className = `badge ${live ? "green" : "amber"}`;
  $("#connection-button").textContent = live ? "Sign out" : "Sign in as admin";
  $("#sidebar-mode").innerHTML = live
    ? "Connected workspace<small>Firebase · authenticated admin</small>"
    : "Demo workspace<small>Sample data · no live changes</small>";
}
async function render() {
  const run = ++generation;
  page = location.hash.slice(1) in titles ? location.hash.slice(1) : "overview";
  $("#breadcrumb").textContent = titles[page];
  $$("[data-page]").forEach((a) => {
    a.classList.toggle("active", a.dataset.page === page);
    a.toggleAttribute("aria-current", a.dataset.page === page);
  });
  $("#sidebar").classList.remove("open");
  mode();
  $("#main").innerHTML =
    head() + '<div class="panel empty">Loading workspace…</div>';
  try {
    let html;
    if (page === "overview") {
      const [stats, us, ev, pol] = await Promise.all([
        api("/admin/stats"),
        api("/admin/users"),
        api("/admin/events"),
        api("/admin/settings"),
      ]);
      if (run !== generation) return;
      users = us.items;
      events = ev.items;
      policy = pol;
      html = overview(stats);
    } else if (page === "users") {
      const data = await api("/admin/users");
      if (run !== generation) return;
      users = data.items;
      userCursor = data.nextCursor;
      html = usersPage();
    } else if (page === "logs") {
      const data = await api("/admin/events");
      if (run !== generation) return;
      events = data.items;
      eventCursor = data.nextCursor;
      html = logsPage();
    } else if (page === "notifications") {
      const [data, us] = await Promise.all([
        api("/admin/notifications"),
        api("/admin/users"),
      ]);
      if (run !== generation) return;
      notifications = data.items;
      noticeCursor = data.nextCursor;
      users = us.items;
      html = notificationsPage();
    } else if (["versions", "maintenance"].includes(page)) {
      policy = await api("/admin/settings");
      if (run !== generation) return;
      html = page === "versions" ? versionsPage() : maintenancePage();
    } else html = integrationPage();
    if (run !== generation) return;
    $("#main").innerHTML = html;
    bind();
  } catch (error) {
    if (run !== generation) return;
    $("#main").innerHTML =
      head() +
      `<div class="error-box">${escape(error.message)}</div><button class="button btn btn-outline-secondary" id="retry">Try again</button>`;
    $("#retry").onclick = render;
  }
}
function stat(label, value, type, foot) {
  return `<div class="stat"><div class="stat-top">${label}<span class="stat-icon">${icon(type)}</span></div><div class="stat-number">${num(value)}</div><div class="stat-foot">${foot}</div></div>`;
}
function overview(stats) {
  const requestRows = [...events]
    .sort((a, b) => b.createdAt - a.createdAt)
    .slice(0, 5);
  return (
    head(
      `<button class="button btn btn-outline-secondary" id="refresh">${icon("refresh")} Refresh</button><a class="button primary btn btn-primary" href="#notifications">${icon("bell")} Send notification</a>`,
    ) +
    (!live
      ? `<div class="notice">${icon("shield")}<span>You’re viewing a demo workspace. Connect Firebase to manage your live application.</span></div>`
      : "") +
    `<div class="stats-grid">${stat("Registered users", stats.users, "users", "Users registered through the mobile API")}${stat("Online now", stats.online, "activity", "Heartbeat received in the last 5 minutes")}${stat("App installations", stats.installations, "download", "Unique installation IDs · not store downloads")}${stat("Multi-device users", stats.multiDevice, "phone", "Accounts linked to more than one device")}</div>
 <div class="grid-two"><section class="panel card"><div class="panel-head card-header"><div><h2>Application activity</h2><small>${live ? "Recorded request totals" : "Illustrative 7-day trend · demo only"}</small></div><div class="chart-legend"><span><i class="legend-dot"></i>Requests</span><span><i class="legend-dot pale"></i>Active users</span></div></div><div class="panel-body card-body">${live ? `<div class="fields"><div class="detail-stat"><small>Recorded requests</small><strong>${num(stats.requests)}</strong></div><div class="detail-stat"><small>Client-reported errors</small><strong>${num(stats.errors)}</strong></div></div><p class="hint">Cumulative mobile telemetry. Requests are reported by your app, not automatically captured by Firebase.</p>` : chart()}</div></section>
 <section class="panel card"><div class="panel-head card-header"><div><h2>Application status</h2><small>Current release and availability</small></div>${badge(live ? "Live configuration" : "Demo configuration", live ? "green" : "blue")}</div><div class="panel-body card-body"><div class="health-item"><span class="stat-icon">${icon("shield")}</span><div><strong>Maintenance mode</strong><small>Application availability</small></div>${badge(policy.maintenance ? "Enabled" : "Disabled", policy.maintenance ? "amber" : "green")}</div><div class="health-item"><span class="stat-icon">${icon("phone")}</span><div><strong>Android release</strong><small>Minimum build ${policy.android.minimumBuild}</small></div>${badge("v" + policy.android.version, "blue")}</div><div class="health-item"><span class="stat-icon">${icon("phone")}</span><div><strong>iOS release</strong><small>Minimum build ${policy.ios.minimumBuild}</small></div>${badge("v" + policy.ios.version, "blue")}</div></div></section></div>
 <section class="panel card"><div class="panel-head card-header"><div><h2>Recent application requests</h2><small>Latest records in the loaded sample</small></div><a class="link-button" href="#logs">View all requests →</a></div>${eventTable(requestRows, true)}<div class="table-foot"><span>Updated ${date(stats.updatedAt)}</span><span>${live ? "Live API response" : "Sample records"}</span></div></section>`
  );
}
function eventTable(items, compact = false) {
  return `<div class="table-wrap"><table><thead><tr><th>User / request</th><th>Feature</th><th>Status</th><th>Response time</th><th>Time</th><th></th></tr></thead><tbody>${items.map((e) => `<tr><td><strong>${escape(e.uid)}</strong><small>${escape(e.method)} ${escape(e.path)}</small></td><td>${escape(e.feature)}</td><td>${badge(e.status + " " + (e.status >= 400 ? "Error" : "Success"), e.status >= 400 ? "red" : "green")}</td><td>${num(e.durationMs)} ms</td><td>${date(e.createdAt)}</td><td><button class="link-button" data-event="${escape(e.id)}">Details →</button></td></tr>`).join("") || '<tr><td colspan="6" class="empty">No matching requests found.</td></tr>'}</tbody></table></div>`;
}
function usersPage() {
  return (
    head(
      `<button class="button btn btn-outline-secondary" id="refresh">${icon("refresh")} Refresh</button>`,
    ) +
    `<div class="toolbar"><div class="search">${icon("search")}<input id="user-search" aria-label="Search loaded users" placeholder="Search loaded users by name, email, or ID…"></div><select id="user-filter" aria-label="Filter users"><option value="all">All users</option><option value="online">Online now</option><option value="multi">Multiple devices</option></select></div><section class="panel card"><div class="panel-head card-header"><h2>User directory</h2><span class="muted hint" id="user-count"></span></div><div id="users-table"></div><div class="table-foot"><span>Online = active in the last 5 minutes · filters apply to loaded records</span><button class="button small btn btn-sm btn-outline-secondary" id="more-users" ${userCursor ? "" : "hidden"}>Load more</button></div></section>`
  );
}
function drawUsers() {
  const query = $("#user-search").value.toLowerCase(),
    filter = $("#user-filter").value;
  const result = users.filter(
    (u) =>
      `${u.name} ${u.email} ${u.id}`.toLowerCase().includes(query) &&
      (filter === "all" ||
        (filter === "online" && online(u)) ||
        (filter === "multi" && u.deviceCount > 1)),
  );
  $("#user-count").textContent =
    `${result.length} of ${users.length} loaded users`;
  $("#users-table").innerHTML =
    `<div class="table-wrap"><table><thead><tr><th>User</th><th>Status</th><th>Devices</th><th>Language</th><th>Last active</th><th></th></tr></thead><tbody>${result.map((u) => `<tr><td><div class="user-cell"><span class="avatar">${escape(initials(u.name))}</span><div><strong>${escape(u.name)}</strong><small>${escape(u.email || u.id)}</small></div></div></td><td>${badge(online(u) ? "Online" : "Offline", online(u) ? "green dot" : "")}</td><td>${u.deviceCount > 1 ? badge(u.deviceCount + " devices", "amber") : escape(u.deviceCount) + " device"}</td><td>${u.language === "ar" ? "Arabic" : "English"}</td><td>${date(u.lastSeen)}</td><td><button class="link-button" data-user="${escape(u.id)}">View profile →</button></td></tr>`).join("") || '<tr><td colspan="6" class="empty">No matching users.</td></tr>'}</tbody></table></div>`;
  bindUserLinks();
}
async function userDetail(uid) {
  try {
    const data = await api("/admin/users/" + encodeURIComponent(uid));
    const u = data.user;
    dialog(
      u.name,
      `<p>${escape(u.email)} · ${escape(u.id)}</p><div class="fields"><div class="detail-stat"><small>Registered devices</small><strong>${u.deviceCount}</strong></div><div class="detail-stat"><small>Last active</small><strong>${date(u.lastSeen)}</strong></div></div><hr class="divider"><h3>Linked devices</h3><div class="table-wrap"><table><thead><tr><th>Device</th><th>Platform</th><th>Version / build</th></tr></thead><tbody>${data.devices.map((d) => `<tr><td><strong>${escape(d.model)}</strong><small>${escape(d.id)}</small></td><td>${escape(d.platform)}</td><td>${escape(d.version)} / ${d.build}</td></tr>`).join("") || '<tr><td colspan="3">No registered devices</td></tr>'}</tbody></table></div><div class="form-actions"><button class="button primary btn btn-primary" id="user-logs">View requests & feature activity</button></div>`,
    );
    $("#user-logs").onclick = () => {
      sessionStorage.setItem("log-user", uid);
      $("#dialog").close();
      location.hash = "logs";
    };
  } catch (e) {
    toast(e.message);
  }
}
function logsPage() {
  return (
    head(
      `<button class="button btn btn-outline-secondary" id="refresh">${icon("refresh")} Refresh</button>`,
    ) +
    `<div class="toolbar"><div class="search">${icon("search")}<input id="log-search" placeholder="Search loaded requests, features, or user ID…" aria-label="Search loaded requests"></div><select id="log-filter" aria-label="Filter request status"><option value="all">All statuses</option><option value="error">Errors only</option><option value="success">Successful</option></select><input id="log-uid" style="width:190px" placeholder="Exact user ID (server filter)" aria-label="User ID"><button class="button btn btn-outline-secondary" id="filter-uid">Apply user</button></div><section class="panel card"><div class="panel-head card-header"><h2>Request explorer</h2><span class="hint">Client-reported telemetry</span></div><div id="logs-table"></div><div class="table-foot"><span id="log-count"></span><button class="button small btn btn-sm btn-outline-secondary" id="more-events" ${eventCursor ? "" : "hidden"}>Load more</button></div></section>`
  );
}
function drawLogs() {
  const q = $("#log-search").value.toLowerCase(),
    f = $("#log-filter").value;
  const result = events
    .filter(
      (e) =>
        `${e.uid} ${e.feature} ${e.path} ${e.message}`
          .toLowerCase()
          .includes(q) &&
        (f === "all" ||
          (f === "error" && e.status >= 400) ||
          (f === "success" && e.status < 400)),
    )
    .sort((a, b) => b.createdAt - a.createdAt);
  $("#logs-table").innerHTML = eventTable(result);
  $("#log-count").textContent =
    `${result.length} matching · ${events.length} loaded records`;
  bindEvents();
}
function bindEvents() {
  $$("[data-event]").forEach(
    (b) =>
      (b.onclick = () => {
        const e = events.find((x) => x.id === b.dataset.event);
        dialog(
          "Request details",
          `<div class="row spaced"><h3>${escape(e.feature)}</h3>${badge(e.status, e.status >= 400 ? "red" : "green")}</div><p>${escape(e.uid)} · ${date(e.createdAt)}</p><div class="endpoint">${escape(e.method)} ${escape(e.path)}</div><hr class="divider"><h3>Problem / activity log</h3><div class="endpoint">${escape(e.message)}</div><p class="hint">Duration: ${num(e.durationMs)} ms · Request ID: ${escape(e.id)}</p><button class="button btn btn-outline-secondary" id="request-user">View user profile</button>`,
        );
        $("#request-user").onclick = () => userDetail(e.uid);
      }),
  );
}
function bindUserLinks() {
  $$("[data-user]").forEach(
    (b) => (b.onclick = () => userDetail(b.dataset.user)),
  );
}
function notificationsPage() {
  return (
    head() +
    `<div class="grid-two"><section class="panel card"><div class="panel-head card-header"><h2>Compose notification</h2>${badge("Firebase Cloud Messaging", "blue")}</div><form class="panel-body card-body" id="push-form"><div class="field"><label for="audience">Audience</label><select id="audience"><option value="single">Single user</option><option value="multiple">Multiple users</option></select></div><div class="field"><label>Recipients</label><div id="recipients"></div><small>Choose from loaded users, or enter exact user IDs below. Up to 100 per request.</small></div><div class="field"><label for="custom-ids">Additional user IDs (optional)</label><input id="custom-ids" placeholder="usr_1001, usr_1002"><small>Comma-separated IDs. In single-user mode, a custom ID replaces the selection.</small></div><div class="field"><label for="push-title">Notification title</label><input id="push-title" maxlength="100" placeholder="e.g. Your next update is here" required></div><div class="field"><label for="push-body">Message</label><textarea id="push-body" maxlength="1000" placeholder="Write your message…" required></textarea><small id="message-count">0 / 1,000 characters</small></div><div class="form-actions"><span class="hint">${live ? "Review recipients before sending" : "Demo: nothing will be sent"}</span><button class="button primary btn btn-primary" type="submit">${icon("bell")} Review notification</button></div></form></section><section class="panel card"><div class="panel-head card-header"><h2>Notification preview</h2>${badge("Mobile")}</div><div class="panel-body card-body"><div class="phone"><div class="phone-notch"></div><div class="phone-top"><span>9:41</span><span>● ▰</span></div><div class="phone-date">Wednesday, 7 October</div><div class="phone-time">9:41</div><div class="push-card"><div class="row"><span class="app-icon" style="width:16px;height:16px;font-size:9px">M</span>MOBILE APPLICATION <span style="margin-left:auto">now</span></div><strong id="preview-title">Notification title</strong><span id="preview-body">Your message will appear here.</span></div></div><p class="hint center">Appearance may vary by device and operating system.</p></div></section></div><section class="panel card"><div class="panel-head card-header"><div><h2>Notification history</h2><small>Accepted by FCM does not confirm delivery to the device.</small></div></div><div id="notice-table">${notificationTable()}</div><div class="table-foot"><span>Latest in loaded records</span><button class="button small btn btn-sm btn-outline-secondary" id="more-notices" ${noticeCursor ? "" : "hidden"}>Load more</button></div></section>`
  );
}
function notificationTable() {
  return `<div class="table-wrap"><table><thead><tr><th>Notification</th><th>Recipients</th><th>Status</th><th>FCM accepted / failed</th><th>Created</th></tr></thead><tbody>${
    [...notifications]
      .sort((a, b) => b.createdAt - a.createdAt)
      .map(
        (n) =>
          `<tr><td><strong>${escape(n.title)}</strong><small>${escape(n.body).slice(0, 100)}</small></td><td>${n.userIds.length} users</td><td>${badge(n.status, n.status === "accepted" ? "green" : n.status === "simulated" ? "blue" : "amber")}</td><td>${n.accepted ?? "—"} / ${n.failed ?? "—"}</td><td>${date(n.createdAt)}</td></tr>`,
      )
      .join("") ||
    '<tr><td colspan="5" class="empty">No notifications yet.</td></tr>'
  }</tbody></table></div>`;
}
function drawRecipients() {
  const multi = $("#audience").value === "multiple";
  $("#recipients").innerHTML = multi
    ? `<div class="checklist">${users.map((u) => `<label><input type="checkbox" name="recipient" value="${escape(u.id)}">${escape(u.name)} <span class="muted">${escape(u.id)}</span></label>`).join("") || '<p class="hint">No loaded users. Enter IDs below.</p>'}</div>`
    : `<select id="single-user" aria-label="Recipient"><option value="">Select a user</option>${users.map((u) => `<option value="${escape(u.id)}">${escape(u.name)} · ${escape(u.id)}</option>`).join("")}</select>`;
}
function platformFields(platform) {
  const p = policy[platform],
    label = platform === "ios" ? "iOS / App Store" : "Android / Google Play";
  return `<section class="panel card"><div class="panel-head card-header"><h2>${label}</h2>${badge("v" + p.version, "blue")}</div><div class="panel-body card-body"><div class="fields"><div class="field"><label for="${platform}-version">Release version</label><input id="${platform}-version" value="${escape(p.version)}" maxlength="30" required></div><div class="field"><label for="${platform}-latest">Latest build number</label><input id="${platform}-latest" type="number" min="1" max="2147483647" value="${p.latestBuild}" required></div></div><div class="field"><label for="${platform}-minimum">Minimum supported build</label><input id="${platform}-minimum" type="number" min="1" max="2147483647" value="${p.minimumBuild}" required><small>Older builds must update. Supported builds below latest get an optional update.</small></div><div class="field"><label for="${platform}-url">Store URL</label><input type="url" id="${platform}-url" value="${escape(p.storeUrl)}" required maxlength="500"></div></div></section>`;
}
function versionsPage() {
  return (
    head() +
    `<form id="version-form"><div class="integration-grid">${platformFields("android")}${platformFields("ios")}</div><section class="panel card" style="margin-top:22px"><div class="panel-head card-header"><h2>Localized update message</h2>${badge("English + العربية")}</div><div class="panel-body card-body"><div class="fields"><div class="field"><label for="update-en">English</label><textarea id="update-en" required maxlength="1000">${escape(policy.updateEn)}</textarea></div><div class="field"><label for="update-ar">Arabic</label><textarea id="update-ar" dir="rtl" lang="ar" required maxlength="1000">${escape(policy.updateAr)}</textarea></div></div><div class="form-actions"><button type="button" class="button btn btn-outline-secondary" id="test-policy">Preview update behavior</button><button class="button primary btn btn-primary" type="submit">Save version policy</button></div></div></section></form>`
  );
}
function collectPolicy() {
  const p = structuredClone(policy);
  for (const platform of ["android", "ios"]) {
    p[platform] = {
      version: $(`#${platform}-version`).value.trim(),
      latestBuild: Number($(`#${platform}-latest`).value),
      minimumBuild: Number($(`#${platform}-minimum`).value),
      storeUrl: $(`#${platform}-url`).value.trim(),
    };
    const v = p[platform];
    if (v.minimumBuild > v.latestBuild)
      throw Error(`${platform}: minimum build cannot exceed latest build.`);
    const url = new URL(v.storeUrl);
    if (
      url.protocol !== "https:" ||
      url.hostname !==
        (platform === "ios" ? "apps.apple.com" : "play.google.com") ||
      url.username ||
      url.password ||
      url.port
    )
      throw Error("Use the official HTTPS App Store / Google Play URL.");
  }
  p.updateEn = $("#update-en").value.trim();
  p.updateAr = $("#update-ar").value.trim();
  return p;
}
function previewPolicy() {
  try {
    if (!$("#version-form").reportValidity()) return;
    const p = collectPolicy();
    dialog(
      "Update behavior preview",
      `<div class="fields"><div class="field"><label for="test-platform">Platform</label><select id="test-platform"><option value="android">Android</option><option value="ios">iOS</option></select></div><div class="field"><label for="test-language">User language</label><select id="test-language"><option value="en">English</option><option value="ar">العربية</option></select></div></div><div class="field"><label for="test-build">Installed build number</label><input type="number" id="test-build" min="1" value="${Math.max(1, p.android.minimumBuild - 1)}"></div><div id="policy-result"></div><p class="hint">Preview uses unsaved form values. The mobile app must handle the returned action and open the store URL.</p>`,
    );
    const update = () => {
      const platform = $("#test-platform").value,
        lang = $("#test-language").value,
        build = Number($("#test-build").value),
        v = p[platform],
        required = build < v.minimumBuild,
        available = build < v.latestBuild;
      if (!Number.isInteger(build) || build < 1) {
        $("#policy-result").textContent =
          "Enter a positive integer build number.";
        return;
      }
      const action = p.maintenance
          ? "maintenance"
          : required
            ? "force_update"
            : available
              ? "optional_update"
              : "allow",
        message = p.maintenance
          ? lang === "ar"
            ? p.maintenanceAr
            : p.maintenanceEn
          : available
            ? lang === "ar"
              ? p.updateAr
              : p.updateEn
            : "Your application is up to date.";
      $("#policy-result").innerHTML =
        `<div class="phone-modal" style="margin:10px 0;border:1px solid var(--border)" dir="${lang === "ar" ? "rtl" : "ltr"}">${badge(action, required ? "amber" : "green")}<h3>${lang === "ar" ? "حالة التطبيق" : "Application status"}</h3><p>${escape(message)}</p>${available && !p.maintenance ? `<a class="button primary btn btn-primary" href="${escape(v.storeUrl)}" target="_blank" rel="noopener noreferrer">${lang === "ar" ? "تحديث الآن" : "Update now"}</a>` : ""}</div>`;
    };
    ["test-platform", "test-language", "test-build"].forEach(
      (id) => ($("#" + id).oninput = update),
    );
    update();
  } catch (e) {
    toast(e.message);
  }
}
function maintenancePage() {
  return (
    head() +
    `<div class="grid-two"><section class="panel card"><div class="panel-head card-header"><h2>Maintenance settings</h2>${badge(policy.maintenance ? "Enabled" : "Disabled", policy.maintenance ? "amber" : "green")}</div><form class="panel-body card-body" id="maintenance-form"><div class="switch-row"><div><h3 style="margin:0">Maintenance mode</h3><p>Ask your app to temporarily restrict access.</p></div><input type="checkbox" class="switch" id="maintenance-enabled" aria-label="Enable maintenance" ${policy.maintenance ? "checked" : ""}></div><div class="field"><label for="maintenance-en">English message</label><textarea id="maintenance-en" required maxlength="1000">${escape(policy.maintenanceEn)}</textarea></div><div class="field"><label for="maintenance-ar">Arabic message</label><textarea id="maintenance-ar" dir="rtl" lang="ar" required maxlength="1000">${escape(policy.maintenanceAr)}</textarea></div><div class="notice">${icon("clock")}<span>The app checks this policy on launch, sign-in, and resume. Maintenance takes priority over updates.</span></div><div class="form-actions"><button class="button primary btn btn-primary" type="submit">Save maintenance settings</button></div></form></section><section class="panel card"><div class="panel-head card-header"><h2>Mobile preview</h2><select id="maintenance-preview-language" aria-label="Preview language"><option value="en">English</option><option value="ar">العربية</option></select></div><div class="panel-body card-body"><div class="phone"><div class="phone-notch"></div><div class="phone-top"><span>9:41</span><span>● ▰</span></div><div class="phone-modal" id="maintenance-preview"></div></div><p class="hint center">Preview only · changes take effect after saving.</p></div></section></div>`
  );
}
function maintenancePreview() {
  const ar = $("#maintenance-preview-language").value === "ar",
    enabled = $("#maintenance-enabled").checked;
  $("#maintenance-preview").dir = ar ? "rtl" : "ltr";
  $("#maintenance-preview").innerHTML =
    `<span class="stat-icon" style="margin:auto">${icon(enabled ? "tool" : "check")}</span><h3>${enabled ? (ar ? "صيانة التطبيق" : "We’ll be back soon") : ar ? "التطبيق متاح" : "Application available"}</h3><p>${enabled ? escape($("#maintenance-" + (ar ? "ar" : "en")).value) : ar ? "يمكنك استخدام التطبيق الآن." : "Your users can continue using the app."}</p><span class="badge ${enabled ? "amber" : "green"}">${enabled ? (ar ? "الصيانة مفعّلة" : "Maintenance enabled") : ar ? "الصيانة غير مفعّلة" : "Maintenance disabled"}</span>`;
}
function databasePanel() {
  const rows = [
    ["users", "Users, language, presence, linked-device count"],
    ["installations", "Devices, FCM tokens, platform and installed version"],
    ["events", "User requests, features and diagnostic logs"],
    ["notifications", "Push requests, recipients and FCM results"],
    ["settings / application", "Localized maintenance and version policy"],
    ["audit", "Administrator configuration changes"],
    ["_meta / schema", "Database schema version and initialization metadata"],
  ];
  return `<section class="panel card" style="margin-bottom:22px"><div class="panel-head card-header"><div><h2>Firestore database setup</h2><small>Project: ${escape(window.PANEL_CONFIG.firebase.projectId)} · (default) database</small></div>${badge("Schema v1", "blue")}</div><div class="panel-body card-body"><p id="database-status" role="status">${live ? "Checking database…" : "Sign in to inspect or initialize the real database. Demo mode does not write to Firebase."}</p><p class="hint">Collections are created on the first real write. Initialization adds only missing configuration and an audit record; it does not create sample users or reset settings.</p></div><div class="table-wrap"><table><thead><tr><th>Collection / document</th><th>Purpose</th></tr></thead><tbody>${rows.map(([name, purpose]) => `<tr><td><strong>${name}</strong></td><td>${purpose}</td></tr>`).join("")}</tbody></table></div><div class="panel-body card-body"><div id="database-collections" class="hint"></div><div class="form-actions"><button class="button btn btn-outline-secondary" id="database-refresh" ${live ? "" : "disabled"}>Check database</button><button class="button primary btn btn-primary" id="database-initialize" ${live ? "" : "disabled"}>Initialize database</button></div></div></section>`;
}
async function loadDatabaseStatus() {
  const result = await api("/admin/database");
  if (!$("#database-status")) return;
  $("#database-status").textContent = result.initialized
    ? "Configuration initialized. Collection availability is shown below."
    : "Configuration has not been initialized with this schema version.";
  $("#database-collections").textContent = result.collections
    .map(
      (c) =>
        `${c.name}: ${c.hasDocuments ? "contains documents" : "ready for first write"}`,
    )
    .join(" · ");
}
function integrationPage() {
  const endpoints = [
    [
      "GET",
      "/admin/stats",
      "Registered users, online users, installations, multi-device accounts.",
    ],
    ["GET", "/admin/users/{uid}", "User profile and linked devices."],
    [
      "GET",
      "/admin/events?uid={uid}",
      "User requests, features, and problem logs.",
    ],
    [
      "POST",
      "/admin/notifications",
      "Send to one or multiple user IDs. Requires Idempotency-Key.",
    ],
    [
      "GET",
      "/mobile/config?platform=ios&build=241&language=ar",
      "Localized maintenance / update action and store URL.",
    ],
    [
      "POST",
      "/mobile/devices",
      "Register device, installation, language, version, and FCM token.",
    ],
    [
      "POST",
      "/mobile/heartbeat",
      "Update online presence. Send every 60 seconds while active.",
    ],
    [
      "POST",
      "/mobile/events",
      "Report sanitized request metadata and application problems.",
    ],
    [
      "DELETE",
      "/mobile/devices/{id}",
      "Unlink the device on logout before signing out.",
    ],
  ];
  return (
    head(
      `<a class="button btn btn-outline-secondary" href="/swagger-ui.html" target="_blank" rel="noopener">${icon("code")} Open Swagger</a>`,
    ) +
    `${databasePanel()}<div class="integration-grid"><section class="panel card"><div class="panel-head card-header"><h2>Administrator access</h2>${badge(live ? "Signed in" : "Sign-in required", live ? "green" : "amber")}</div><div class="panel-body card-body"><div class="step"><span class="step-number">1</span><div><strong>Configure your Firebase project</strong><p>Enable Authentication, Firestore, and Cloud Messaging. Add the public web configuration to assets/js/config/firebase-config.js.</p></div></div><div class="step"><span class="step-number">2</span><div><strong>Start the Spring Boot backend</strong><p>Set FIREBASE_ENABLED, FIREBASE_PROJECT_ID, and server-side Application Default Credentials. Keep service-account keys out of the browser and repository.</p></div></div><div class="step"><span class="step-number">3</span><div><strong>Authorize an administrator</strong><p>Create an email/password Firebase Auth account and grant its admin custom claim with a trusted Admin SDK environment.</p></div></div><div class="step"><span class="step-number">4</span><div><strong>Connect your mobile app</strong><p>Register installations and tokens; send heartbeats and sanitized telemetry. Check the application policy before opening the app.</p></div></div><button class="button primary btn btn-primary" id="integration-connect">${live ? "Manage session" : "Sign in as admin"}</button><hr class="divider"><h3>Authorization</h3><div class="endpoint">Authorization: Bearer &lt;Firebase ID token&gt;</div><p class="hint">Admin endpoints require admin=true. Mobile endpoints derive the user ID from the token. Configuration lookup is public.</p></div></section><section class="panel card"><div class="panel-head card-header"><h2>REST API reference</h2>${badge("Spring Boot", "blue")}</div><div class="panel-body card-body">${endpoints.map(([method, url, desc]) => `<div class="endpoint-row">${badge(method, method === "GET" ? "green" : "blue")} <code>${escape(url)}</code><p>${desc}</p></div>`).join("")}</div></section></div>`
  );
}
async function connection() {
  if (live) {
    await signOut();
    toast("Signed out. Demo workspace restored.");
    render();
    return;
  }
  dialog(
    "Connect your Firebase workspace",
    `<div class="connection-status connection-dialog" data-connection-summary>${connectionSummary()}</div><p>Sign in with a Firebase Authentication administrator account. Configure the project in assets/js/config/firebase-config.js and start the configured backend first.</p><form id="login-form"><div class="field"><label for="email">Admin email</label><input type="email" id="email" autocomplete="username" required></div><div class="field"><label for="password">Password</label><input type="password" id="password" autocomplete="current-password" required></div><div id="login-error" role="alert"></div><div class="form-actions"><button class="button primary btn btn-primary" type="submit">Sign in securely</button></div></form>`,
  );
  updateConnectionView(live);
  refreshConnection();
  $("#login-form").onsubmit = async (e) => {
    e.preventDefault();
    const button = $("button[type=submit]", e.target);
    button.disabled = true;
    $("#login-error").textContent = "";
    try {
      await signIn($("#email").value, $("#password").value);
      $("#dialog").close();
      toast("Signed in. Database workspace loaded.");
      render();
    } catch (error) {
      $("#login-error").textContent = error.message;
      $("#login-error").className = "error-box";
    } finally {
      button.disabled = false;
    }
  };
}
$("#connection-button").onclick = connection;
async function busy(button, task) {
  button.disabled = true;
  try {
    await task();
  } catch (error) {
    toast(error.message);
  } finally {
    button.disabled = false;
  }
}
function bind() {
  if ($("#refresh")) $("#refresh").onclick = render;
  bindEvents();
  bindUserLinks();
  if (page === "users") {
    drawUsers();
    $("#user-search").oninput = drawUsers;
    $("#user-filter").onchange = drawUsers;
    $("#more-users").onclick = (e) =>
      busy(e.target, async () => {
        const data = await api(
          "/admin/users?cursor=" + encodeURIComponent(userCursor),
        );
        users.push(...data.items);
        userCursor = data.nextCursor;
        e.target.hidden = !userCursor;
        drawUsers();
      });
  }
  if (page === "logs") {
    drawLogs();
    $("#log-search").oninput = drawLogs;
    $("#log-filter").onchange = drawLogs;
    $("#filter-uid").onclick = (e) =>
      busy(e.target, async () => {
        const data = await api(
          "/admin/events?uid=" + encodeURIComponent($("#log-uid").value.trim()),
        );
        events = data.items;
        eventCursor = data.nextCursor;
        $("#more-events").hidden = !eventCursor;
        drawLogs();
      });
    $("#more-events").onclick = (e) =>
      busy(e.target, async () => {
        const data = await api(
          "/admin/events?cursor=" +
            encodeURIComponent(eventCursor) +
            "&uid=" +
            encodeURIComponent($("#log-uid").value.trim()),
        );
        events.push(...data.items);
        eventCursor = data.nextCursor;
        e.target.hidden = !eventCursor;
        drawLogs();
      });
    const uid = sessionStorage.getItem("log-user");
    if (uid) {
      sessionStorage.removeItem("log-user");
      $("#log-uid").value = uid;
      $("#filter-uid").click();
    }
  }
  if (page === "notifications") {
    drawRecipients();
    $("#audience").onchange = drawRecipients;
    $("#push-title").oninput = () =>
      ($("#preview-title").textContent =
        $("#push-title").value || "Notification title");
    $("#push-body").oninput = () => {
      $("#preview-body").textContent =
        $("#push-body").value || "Your message will appear here.";
      $("#message-count").textContent =
        `${$("#push-body").value.length} / 1,000 characters`;
    };
    $("#push-form").onsubmit = (e) => {
      e.preventDefault();
      const custom = $("#custom-ids")
        .value.split(",")
        .map((x) => x.trim())
        .filter(Boolean);
      let ids =
        $("#audience").value === "single"
          ? custom.length
            ? custom
            : [$("#single-user").value]
          : [...$$("[name=recipient]:checked").map((c) => c.value), ...custom];
      ids = [...new Set(ids.filter(Boolean))];
      if (
        !ids.length ||
        ids.length > 100 ||
        ids.some((x) => x.length > 128 || x.includes("/"))
      )
        return toast("Select between 1 and 100 valid user IDs.");
      if ($("#audience").value === "single" && ids.length !== 1)
        return toast("Single-user mode requires exactly one recipient.");
      const body = {
        userIds: ids,
        title: $("#push-title").value.trim(),
        body: $("#push-body").value.trim(),
      };
      if (!body.title || !body.body) return toast("Enter a title and message.");
      const key = crypto.randomUUID();
      dialog(
        "Review notification",
        `<p>${live ? "This will send a push notification" : "Demo simulation only"} to <strong>${ids.length} user(s)</strong>.</p><div class="endpoint">${ids.map(escape).join(", ")}</div><hr class="divider"><h3>${escape(body.title)}</h3><p>${escape(body.body)}</p><div class="form-actions"><button class="button primary btn btn-primary" id="confirm-send">${live ? "Send notification" : "Simulate notification"}</button></div>`,
      );
      $("#confirm-send").onclick = (e) =>
        busy(e.target, async () => {
          const result = await api("/admin/notifications", {
            method: "POST",
            headers: { "Idempotency-Key": key },
            body,
          });
          $("#dialog").close();
          toast(
            live
              ? `Notification status: ${result.status}. FCM accepted: ${result.accepted ?? 0}.`
              : "Demo notification saved. No push was sent.",
          );
          render();
        });
    };
    $("#more-notices").onclick = (e) =>
      busy(e.target, async () => {
        const data = await api(
          "/admin/notifications?cursor=" + encodeURIComponent(noticeCursor),
        );
        notifications.push(...data.items);
        noticeCursor = data.nextCursor;
        e.target.hidden = !noticeCursor;
        $("#notice-table").innerHTML = notificationTable();
      });
  }
  if (page === "versions") {
    $("#test-policy").onclick = previewPolicy;
    $("#version-form").onsubmit = (e) => {
      e.preventDefault();
      busy($("button[type=submit]", e.target), async () => {
        policy = await api("/admin/settings", {
          method: "PUT",
          body: collectPolicy(),
        });
        toast(live ? "Version policy saved." : "Demo version policy saved.");
        render();
      });
    };
  }
  if (page === "maintenance") {
    maintenancePreview();
    [
      "maintenance-enabled",
      "maintenance-en",
      "maintenance-ar",
      "maintenance-preview-language",
    ].forEach((id) => ($("#" + id).oninput = maintenancePreview));
    $("#maintenance-form").onsubmit = (e) => {
      e.preventDefault();
      const body = {
        ...policy,
        maintenance: $("#maintenance-enabled").checked,
        maintenanceEn: $("#maintenance-en").value.trim(),
        maintenanceAr: $("#maintenance-ar").value.trim(),
      };
      if (!body.maintenanceEn || !body.maintenanceAr)
        return toast("Both localized messages are required.");
      dialog(
        "Save maintenance settings",
        `<p>${body.maintenance ? "Enable maintenance and ask the app to restrict access?" : "Save these messages with maintenance disabled?"}</p><p>${live ? "This updates the live application policy." : "This changes the demo workspace only."}</p><div class="form-actions"><button id="confirm-maintenance" class="button primary btn btn-primary">Save settings</button></div>`,
      );
      $("#confirm-maintenance").onclick = (e) =>
        busy(e.target, async () => {
          policy = await api("/admin/settings", { method: "PUT", body });
          $("#dialog").close();
          toast("Maintenance settings saved.");
          render();
        });
    };
  }
  if ($("#integration-connect")) $("#integration-connect").onclick = connection;
  if ($("#database-refresh")) {
    $("#database-refresh").onclick = (e) => busy(e.target, loadDatabaseStatus);
    $("#database-initialize").onclick = (e) =>
      busy(e.target, async () => {
        const status = await api("/admin/database");
        if (status.projectId !== window.PANEL_CONFIG.firebase.projectId)
          throw Error(
            "Frontend and backend Firebase project IDs differ. Check the server configuration.",
          );
        dialog(
          "Initialize Firestore configuration",
          `<p>Project: <strong>${escape(status.projectId)}</strong> · database: (default)</p><p>Create missing settings/application and _meta/schema documents, with an audit entry. Existing settings and application data are preserved. Empty data collections appear automatically on the first real API write.</p><div class="form-actions"><button class="button primary btn btn-primary" id="confirm-database-init">Initialize missing configuration</button></div>`,
        );
        $("#confirm-database-init").onclick = (e) =>
          busy(e.target, async () => {
            const result = await api("/admin/database/initialize", {
              method: "POST",
            });
            $("#dialog").close();
            toast(result.message);
            await loadDatabaseStatus();
          });
      });
    if (live)
      loadDatabaseStatus().catch((e) => {
        $("#database-status").textContent = e.message;
      });
  }
}
refreshConnection();
window.addEventListener("hashchange", render);
render();
