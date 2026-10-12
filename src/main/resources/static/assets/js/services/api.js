import {
  users,
  devices,
  events,
  initialPolicy,
  initialNotifications,
  initialOccasions,
} from "../data/demo.js";
export let live = false;
let firebaseAuth, authSdk;
const storageKey = "appcontrol-demo-v1";
function loadDemo() {
  try {
    return JSON.parse(localStorage.getItem(storageKey)) || {};
  } catch {
    return {};
  }
}
const saved = loadDemo();
let policy = saved.policy || structuredClone(initialPolicy),
  notifications = saved.notifications || structuredClone(initialNotifications),
  occasions = saved.occasions || structuredClone(initialOccasions);
function persist() {
  localStorage.setItem(storageKey, JSON.stringify({ policy, notifications, occasions }));
}
const clone = (x) => structuredClone(x);
export async function signIn(email, password) {
  if (window.PANEL_CONFIG?.reviewOnly) throw Error("This review preview uses sample data. Admin sign-in is available on the hosted backend.");
  const config = window.PANEL_CONFIG.firebase;
  if (!config.apiKey || !config.projectId)
    throw Error(
      "Add your Firebase web configuration in assets/js/config/firebase-config.js first.",
    );
  const appSdk = await import(
    "https://www.gstatic.com/firebasejs/12.0.0/firebase-app.js"
  );
  authSdk = await import(
    "https://www.gstatic.com/firebasejs/12.0.0/firebase-auth.js"
  );
  const app = appSdk.getApps().length
    ? appSdk.getApp()
    : appSdk.initializeApp(config);
  firebaseAuth = authSdk.getAuth(app);
  await authSdk.setPersistence(firebaseAuth, authSdk.inMemoryPersistence);
  const result = await authSdk.signInWithEmailAndPassword(
    firebaseAuth,
    email,
    password,
  );
  const token = await result.user.getIdTokenResult(true);
  if (token.claims.admin !== true) {
    await authSdk.signOut(firebaseAuth);
    throw Error("This account does not have the admin claim.");
  }
  live = true;
  try {
    await api("/admin/stats");
  } catch (error) {
    await signOut();
    throw error;
  }
}
export async function signOut() {
  if (firebaseAuth) await authSdk.signOut(firebaseAuth);
  live = false;
}
export async function api(path, options = {}) {
  if (!live) return demo(path, options);
  const token = await firebaseAuth.currentUser?.getIdToken();
  if (!token) throw Error("Your session expired. Sign in again.");
  const res = await fetch(window.PANEL_CONFIG.apiBase + path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  const data = await res
    .json()
    .catch(() => ({ error: "Unexpected server response" }));
  if (!res.ok) throw Error(data.error || `Request failed (${res.status})`);
  return data;
}
function demo(path, options) {
  const url = new URL(path, "https://demo.local"),
    p = url.pathname;
  if (p === "/admin/stats")
    return Promise.resolve({
      users: users.length,
      online: users.filter((u) => u.lastSeen > Date.now() - 300000).length,
      installations: devices.length,
      multiDevice: users.filter((u) => u.deviceCount > 1).length,
      requests: events.length,
      errors: events.filter((e) => e.status >= 400).length,
      updatedAt: Date.now(),
    });
  if (p === "/admin/users")
    return Promise.resolve({ items: clone(users), nextCursor: "" });
  if (p.startsWith("/admin/users/")) {
    const uid = decodeURIComponent(p.split("/").pop());
    return Promise.resolve({
      user: clone(users.find((u) => u.id === uid)),
      devices: clone(devices.filter((d) => d.uid === uid)),
    });
  }
  if (p === "/admin/events")
    return Promise.resolve({
      items: clone(
        events.filter(
          (e) =>
            !url.searchParams.get("uid") ||
            e.uid === url.searchParams.get("uid"),
        ),
      ),
      nextCursor: "",
    });
  if (p === "/admin/settings") {
    if (options.method === "PUT") {
      policy = clone(options.body);
      persist();
    }
    return Promise.resolve(clone(policy));
  }
  if (p === "/admin/notifications") {
    if (options.method === "POST") {
      const requests=Array.isArray(options.body)?clone(options.body):options.body.userIds.map(id=>({id,title:options.body.title,message:options.body.body}));
      const all=String(requests[0].id)==="0";
      const selected=all?users.map(u=>({...requests[0],id:u.id})):requests;
      const n = {
        requests, audience:all?"all":"selected", userIds:selected.map(r=>String(r.id)),
        title:requests[0].title, body:requests[0].message, recipientCount:selected.length,
        recipients:{items:selected.map(r=>({uid:String(r.id),title:r.title,message:r.message,status:"simulated",accepted:0,failed:0,unknown:0,fcmResults:devices.filter(d=>d.uid===String(r.id)).map(d=>({deviceId:d.id,status:"simulated"}))})),nextCursor:""},
        id: crypto.randomUUID(), status: "simulated", accepted: 0, failed: 0, unknown:0, createdAt: Date.now(),
      };
      notifications.unshift(n);
      persist();
      return Promise.resolve(n);
    }
    return Promise.resolve({ items: clone(notifications), nextCursor: "" });
  }
  if (p.startsWith("/admin/notifications/")) {
    const id=decodeURIComponent(p.split('/')[3]);
    const item=notifications.find(n=>n.id===id);
    if(!item) throw Error("Push request not found");
    return Promise.resolve(clone(p.endsWith('/recipients')?item.recipients || {items:[],nextCursor:""}:item));
  }
  if (p === "/admin/occasions" || p.startsWith("/admin/occasions/")) {
    const id = decodeURIComponent(p.split("/")[3] || "");
    if (options.method === "POST") occasions.push({ ...clone(options.body), id: crypto.randomUUID(), createdAt: Date.now() });
    else if (options.method) {
      const index = occasions.findIndex((o) => o.id === id);
      if (index < 0) throw Error("Occasion not found");
      if (options.method === "DELETE") occasions.splice(index, 1);
      else occasions[index] = { ...occasions[index], ...clone(options.body) };
    }
    if (options.method) persist();
    return Promise.resolve({ items: clone(occasions).sort((a, b) => a.startAt - b.startAt), nextCursor: "" });
  }
  throw Error("Demo endpoint not available");
}
