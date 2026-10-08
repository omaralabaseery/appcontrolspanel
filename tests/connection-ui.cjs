const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const base=process.env.PANEL_TEST_URL||'http://localhost:8089';
(async()=>{
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  let state={projectId:'insta-11358',database:'(default)',status:'connected',checkedAt:Date.now()};
  await page.route('**/api/connection',route=>route.fulfill({json:state}));
  await page.goto(base);
  const strip=page.locator('#connection-status');
  await strip.getByText('Firestore connected',{exact:true}).waitFor();
  assert.match(await strip.innerText(),/Project: insta-11358/);
  assert.match(await strip.innerText(),/Data source: Demo samples/);
  assert.match(await strip.innerText(),/Admin: Not signed in/);
  await page.locator('#connection-button').click();
  await page.locator('[data-connection-summary]').getByText('Firestore connected',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Close dialog'}).click();
  for(const [status,label] of [['not_configured','Firebase backend not configured'],['unavailable','Firestore connection failed']]) {
   state={...state,status};await strip.getByRole('button',{name:'Check connection'}).click();await strip.getByText(label,{exact:true}).waitFor();
  }
  state={...state,status:'connected',projectId:'other-project'};
  await strip.getByRole('button',{name:'Check connection'}).click();await strip.getByText(/Project mismatch/).waitFor();
  await page.unroute('**/api/connection');await page.route('**/api/connection',route=>route.abort());
  await strip.getByRole('button',{name:'Check connection'}).click();await strip.getByText('Backend unavailable',{exact:true}).waitFor();
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  assert.deepEqual(errors,[]);
  console.log('PASS: verified connection, project, demo/admin separation, sign-in dialog, failed/unconfigured backend, mismatch, network failure and mobile layout');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
