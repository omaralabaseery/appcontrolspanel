import { escape } from "../core/ui.js";
export function jsonPanel(value,filename) {
  return `<section class="json-panel"><div class="row spaced"><h3>${escape(filename)}</h3><button type="button" class="button small" data-json-download="${escape(filename)}">Download ${escape(filename)}</button></div>${value == null ? '<p class="hint">No payload was captured for this request.</p>' : ''}<pre tabindex="0"><code>${escape(JSON.stringify(value ?? null,null,2))}</code></pre></section>`;
}
export function bindJsonDownloads(payloads) {
  document.querySelectorAll('[data-json-download]').forEach(button => button.onclick=()=>{
    const filename=button.dataset.jsonDownload;
    const url=URL.createObjectURL(new Blob([JSON.stringify(payloads[filename] ?? null,null,2)],{type:'application/json'}));
    const link=document.createElement('a');link.href=url;link.download=filename;link.click();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
  });
}
export function releaseRows(platform,releases) {
  return releases.map(r=>`<tr data-release-row><td><input type="number" min="1" max="2147483647" data-release-build aria-label="${platform} release build" value="${r.build}" required></td><td><input data-release-version aria-label="${platform} release version" maxlength="30" value="${escape(r.version)}" required></td><td><label class="release-toggle"><input type="checkbox" data-release-enabled ${r.enabled!==false?'checked':''}><span>${r.enabled!==false?'Enabled':'Disabled'}</span></label></td><td><button type="button" class="link-button" data-remove-release>Remove</button></td></tr>`).join('');
}
export function bindReleaseRows() {
  document.querySelectorAll('[data-release-enabled]').forEach(input=>input.onchange=()=>input.nextElementSibling.textContent=input.checked?'Enabled':'Disabled');
  document.querySelectorAll('[data-remove-release]').forEach(button=>button.onclick=()=>button.closest('tr').remove());
}
export function collectReleases(platform) {
  const rows=[...document.querySelectorAll(`#${platform}-releases [data-release-row]`)];
  const releases=rows.map(row=>({build:Number(row.querySelector('[data-release-build]').value),version:row.querySelector('[data-release-version]').value.trim(),enabled:row.querySelector('[data-release-enabled]').checked}));
  if(releases.length>100 || releases.some(r=>!Number.isInteger(r.build)||r.build<1||!r.version)) throw Error('Enter valid build numbers and version names (up to 100 releases).');
  if(new Set(releases.map(r=>r.build)).size!==releases.length) throw Error(`${platform}: duplicate build numbers.`);
  return releases;
}
