const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const createCultureRouter = require('../routes/culture');

async function withApi(pool, run) {
  const app = express();
  app.use('/api/culture', createCultureRouter({ pool }));
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}/api/culture`); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

test('culture search binds keyword, paginates and repairs duplicated image host', async () => {
  const keyword = "音樂' OR 1=1 --";
  await withApi({ execute: async (sql, params) => {
    assert.ok(sql.includes('LIMIT 50 OFFSET 50'));
    assert.ok(!sql.includes(keyword));
    assert.deepEqual(params, [keyword, ...Array(3).fill(`%${keyword}%`)]);
    return [[{ itemId: 'culture:a:1', imageUrl: 'https://cloud.culture.twhttps://cloud.culture.tw/image.jpg' }]];
  } }, async base => {
    const response = await fetch(`${base}/events?offset=50&keyword=${encodeURIComponent(keyword)}`);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.items[0].imageUrl, 'https://cloud.culture.tw/image.jpg');
  });
});

test('venue pagination bounds limits and combines both sources', async () => {
  await withApi({ query: async sql => {
    assert.ok(sql.includes('LIMIT 300 OFFSET 300'));
    assert.ok(sql.includes('defaultdb.venues'));
    assert.ok(sql.includes('defaultdb.opendata_culture_event_shows'));
    return [[{ id: 1, name: '測試場館' }]];
  } }, async base => {
    const response = await fetch(`${base}/venues?limit=999&offset=300`);
    assert.equal(response.status, 200);
    assert.equal((await response.json()).items[0].name, '測試場館');
  });
});
