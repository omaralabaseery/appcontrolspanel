const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const base=process.env.PANEL_TEST_URL||'http://localhost:8089';
(async()=>{
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000},acceptDownloads:true});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(base);await page.getByRole('heading',{name:'Overview',exact:true}).waitFor();assert.equal(await page.locator('#main table').count(),0);
  assert(await page.locator('.brand-logo img').evaluate(img=>img.complete&&img.naturalWidth>0));
  await page.locator('a[data-page=logs]').click();await page.getByRole('heading',{name:'Recent application requests'}).waitFor();
  await page.locator('[data-event]').first().click();await page.getByRole('heading',{name:'Input.json',exact:true}).waitFor();await page.getByRole('heading',{name:'Output.json',exact:true}).waitFor();
  const dl=page.waitForEvent('download');await page.getByRole('button',{name:'Download Input.json',exact:true}).click();const file=await dl;assert.equal(file.suggestedFilename(),'Input.json');assert(JSON.parse(fs.readFileSync(await file.path(),'utf8')).context.screen);
  await page.screenshot({path:'/tmp/panel-request-details.png',fullPage:true});await page.getByRole('button',{name:'Close dialog'}).click();
  await page.locator('a[data-page=notifications]').click();await page.locator('#audience').selectOption('all');assert(await page.locator('#custom-ids').isDisabled());await page.locator('#push-title').fill('All users test');await page.locator('#push-body').fill('Broadcast demo');await page.getByRole('button',{name:'Review notification'}).click();await page.getByText('ALL registered users',{exact:true}).waitFor();await page.getByRole('button',{name:'Simulate notification'}).click();await page.locator('#notice-table').getByText('All users test',{exact:true}).waitFor();
  await page.locator('#notice-table tr').filter({hasText:'All users test'}).getByRole('button',{name:'Details'}).click();await page.getByRole('heading',{name:'Recipients and FCM results',exact:true}).waitFor();assert.match(await page.locator('#dialog').innerText(),/simulated/);assert.match(await page.locator('#dialog').innerText(),/deviceId/);await page.getByRole('button',{name:'Close dialog'}).click();
  await page.locator('a[data-page=versions]').click();const disabled=page.locator('#android-releases tr').filter({has:page.locator('input[data-release-build][value="240"]')});assert.equal(await disabled.locator('[data-release-enabled]').isChecked(),false);
  await page.getByRole('button',{name:'Preview update behavior'}).click();await page.locator('#test-build').fill('240');await page.getByText('version_disabled',{exact:true}).waitFor();await page.getByRole('button',{name:'Close dialog'}).click();
  await disabled.locator('[data-release-enabled]').check();await page.getByRole('button',{name:'Save version policy'}).click();await page.getByRole('heading',{name:'Version control',exact:true}).waitFor();await page.reload();await page.locator('#android-releases').waitFor();assert(await page.locator('#android-releases tr').first().locator('[data-release-enabled]').isChecked());
  await page.locator('#android-releases tr').last().locator('[data-release-enabled]').uncheck();await page.getByRole('button',{name:'Save version policy'}).click();await page.getByRole('status').filter({hasText:'latest build must be enabled'}).waitFor();
  await page.screenshot({path:'/tmp/panel-version-control.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.deepEqual(errors,[]);
  console.log('PASS: overview without table, request JSON and download, logo, all-user simulation and FCM details, version disabled/enabled persistence and latest-build guard, mobile layout');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
