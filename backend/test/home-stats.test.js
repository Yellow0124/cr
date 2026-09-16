const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const createHomeStatsRouter = require('../routes/home-stats');

async function request(pool, authorized = true) {
  const app = express();
  app.use(createHomeStatsRouter({ pool, requireAuth(req, res, next) {
    if (!authorized) return res.sendStatus(401);
    req.user = { id: 42 }; next();
  } }));
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}`);
    return { status: response.status, body: await response.text() };
  } finally { await new Promise(resolve => server.close(resolve)); }
}

test('returns complete database counts, including true zero and account-scoped reminders', async () => {
  const response = await request({ execute: async (sql, params) => {
    assert.deepEqual(params, [42]);
    assert.match(sql, /opendata_culture_events/);
    assert.match(sql, /UNION\s+SELECT location_name/);
    assert.match(sql, /reminders WHERE user_id = \?/);
    assert.doesNotMatch(sql, /LIMIT/);
    return [[{ events: '1234', artists: '200', venues: '555', reminders: '0' }]];
  } });
  assert.equal(response.status, 200);
  assert.deepEqual(JSON.parse(response.body), { events: 1234, artists: 200, venues: 555, reminders: 0 });
});

test('requires authentication before counting', async () => {
  assert.equal((await request({ execute() { assert.fail('must not query'); } }, false)).status, 401);
});

test('database errors do not become misleading zero counts', async () => {
  const response = await request({ execute: async () => { throw new Error('offline'); } });
  assert.equal(response.status, 500);
  assert.deepEqual(JSON.parse(response.body), { error: 'stats_unavailable' });
});
