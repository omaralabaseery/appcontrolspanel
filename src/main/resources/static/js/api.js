import {users,devices,events,initialPolicy,initialNotifications} from './data.js';
export let live=false;
let firebaseAuth, authSdk;
const storageKey='appcontrol-demo-v1';
function loadDemo(){try{return JSON.parse(localStorage.getItem(storageKey))||{};}catch{return {};}}
const saved=loadDemo();
let policy=saved.policy||structuredClone(initialPolicy), notifications=saved.notifications||structuredClone(initialNotifications);
function persist(){localStorage.setItem(storageKey,JSON.stringify({policy,notifications}));}
const clone=x=>structuredClone(x);
export async function signIn(email,password){
  const config=window.PANEL_CONFIG.firebase;
  if(!config.apiKey||!config.projectId)throw Error('Add your Firebase web configuration in config.js first.');
  const appSdk=await import('https://www.gstatic.com/firebasejs/12.0.0/firebase-app.js');
  authSdk=await import('https://www.gstatic.com/firebasejs/12.0.0/firebase-auth.js');
  const app=appSdk.getApps().length?appSdk.getApp():appSdk.initializeApp(config);
  firebaseAuth=authSdk.getAuth(app);
  await authSdk.setPersistence(firebaseAuth,authSdk.inMemoryPersistence);
  const result=await authSdk.signInWithEmailAndPassword(firebaseAuth,email,password);
  const token=await result.user.getIdTokenResult(true);
  if(token.claims.admin!==true){await authSdk.signOut(firebaseAuth);throw Error('This account does not have the admin claim.');}
  live=true;
  try{await api('/admin/stats');}catch(error){await signOut();throw error;}
}
export async function signOut(){if(firebaseAuth)await authSdk.signOut(firebaseAuth);live=false;}
export async function api(path,options={}){
  if(!live)return demo(path,options);
  const token=await firebaseAuth.currentUser?.getIdToken();
  if(!token)throw Error('Your session expired. Sign in again.');
  const res=await fetch(window.PANEL_CONFIG.apiBase+path,{...options,headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`,...options.headers},body:options.body?JSON.stringify(options.body):undefined});
  const data=await res.json().catch(()=>({error:'Unexpected server response'}));
  if(!res.ok)throw Error(data.error||`Request failed (${res.status})`);
  return data;
}
function demo(path,options){
 const url=new URL(path,'https://demo.local'), p=url.pathname;
 if(p==='/admin/stats')return Promise.resolve({users:users.length,online:users.filter(u=>u.lastSeen>Date.now()-300000).length,installations:devices.length,multiDevice:users.filter(u=>u.deviceCount>1).length,requests:events.length,errors:events.filter(e=>e.status>=400).length,updatedAt:Date.now()});
 if(p==='/admin/users')return Promise.resolve({items:clone(users),nextCursor:''});
 if(p.startsWith('/admin/users/')){const uid=decodeURIComponent(p.split('/').pop());return Promise.resolve({user:clone(users.find(u=>u.id===uid)),devices:clone(devices.filter(d=>d.uid===uid))});}
 if(p==='/admin/events')return Promise.resolve({items:clone(events.filter(e=>!url.searchParams.get('uid')||e.uid===url.searchParams.get('uid'))),nextCursor:''});
 if(p==='/admin/settings'){
  if(options.method==='PUT'){policy=clone(options.body);persist();}
  return Promise.resolve(clone(policy));
 }
 if(p==='/admin/notifications'){
  if(options.method==='POST'){
   const n={...clone(options.body),id:crypto.randomUUID(),status:'simulated',accepted:0,failed:0,createdAt:Date.now()};notifications.unshift(n);persist();return Promise.resolve(n);
  }
  return Promise.resolve({items:clone(notifications),nextCursor:''});
 }
 throw Error('Demo endpoint not available');
}
