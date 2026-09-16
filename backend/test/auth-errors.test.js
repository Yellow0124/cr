const test = require('node:test');
const assert = require('node:assert/strict');
const jwt = require('jsonwebtoken');
const auth = require('../auth');

async function check(token, pool) {
  let result;
  await auth({ secret: 'test-only-secret', pool })(
    { headers: { authorization: `Bearer ${token}` } },
    { status(status) { this.code = status; return this; }, json(body) { result = { status: this.code, body }; } },
    () => { result = { status: 200 }; }
  );
  return result;
}

test('fresh token queries the real registration timestamp column', async () => {
  const token = auth.createToken({ id: 1, email: 'test@example.com' }, 'test-only-secret');
  const result = await check(token, { execute: async sql => {
    assert.match(sql, /registered_at AS createdAt/);
    return [[{ id: 1 }]];
  } });
  assert.equal(result.status, 200);
});

test('database failure does not report an expired login', async () => {
  const token = auth.createToken({ id: 1 }, 'test-only-secret');
  const result = await check(token, { execute: async () => { throw new Error('database offline'); } });
  assert.equal(result.status, 503);
  assert.equal(result.body.error, 'auth_service_unavailable');
});

test('expired token is still rejected before querying the database', async () => {
  const token = jwt.sign({ id: 1, exp: 1 }, 'test-only-secret');
  const result = await check(token, { execute: async () => { assert.fail('must not query'); } });
  assert.equal(result.status, 401);
});
