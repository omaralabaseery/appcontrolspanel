// Isolated UI contract test. Firebase and database requests are mocked; no cloud writes.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const base=process.env.PANEL_TEST_URL||'http://localhost:8080';
(async()=>{
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(base+'/#integration');
  await page.getByRole('heading',{name:'Firestore database setup'}).waitFor();
  assert(await page.locator('#database-initialize').isDisabled());
  assert(await page.locator('#database-refresh').isDisabled());
  let initialized=false,writes=0;
  await page.route('**/assets/js/services/api.js',route=>route.fulfill({contentType:'text/javascript',body:`export let live=true; export async function signIn(){} export async function signOut(){} export async function api(path,options={}){ const r=await fetch('/api'+path,{method:options.method||'GET'});return r.json(); }`}));
  await page.route('**/api/admin/**',route=>{
   if(route.request().url().endsWith('/initialize')) {
    assert.equal(route.request().method(),'POST');initialized=true;writes++;
    return route.fulfill({json:{changed:true,message:'Initialized missing configuration.'}});
   }
   return route.fulfill({json:{projectId:'insta-11358',initialized,collections:[{name:'users',hasDocuments:false},{name:'settings',hasDocuments:initialized}]}});
  });
  await page.reload();
  await page.getByText('Configuration has not been initialized with this schema version.').waitFor();
  await page.locator('#database-initialize').click();
  await page.getByRole('heading',{name:'Initialize Firestore configuration'}).waitFor();
  assert.equal(writes,0,'Opening confirmation must not mutate data');
  await page.locator('#confirm-database-init').click();
  await page.getByText('Configuration initialized. Collection availability is shown below.').waitFor();
  assert.equal(writes,1);assert.deepEqual(errors,[]);
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  console.log('PASS: demo writes disabled, live status, explicit initialization, refresh and responsive layout');
 } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
