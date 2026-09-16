const test = require('node:test');
const assert = require('node:assert/strict');

const { createApp } = require('../app');

class FakePool {
  constructor() {
    this.users = [];
    this.reminders = [];
    this.nextUserId = 1;
    this.nextReminderId = 1;
  }

  async execute(sql, params = {}) {
    return this.#run(sql, params);
  }

  async query(sql, params = {}) {
    return this.#run(sql, params);
  }

  async #run(sql, params) {
    const normalized = String(sql).replace(/\s+/g, ' ').trim().toLowerCase();

    if (normalized.includes('select 1 as ok')) {
      return [[{ ok: 1 }]];
    }

    if (normalized.includes('select id, email, password_hash from users where email = :email')) {
      const user = this.users.find(item => item.email === params.email);
      return [[user ? { ...user } : undefined].filter(Boolean)];
    }

    if (normalized.includes('insert into users')) {
      const existing = this.users.find(item => item.email === params.email);
      if (existing) {
        const error = new Error('duplicate');
        error.code = 'ER_DUP_ENTRY';
        throw error;
      }

      const user = {
        id: this.nextUserId++,
        email: params.email,
        password_hash: params.passwordHash,
        created_at: new Date().toISOString()
      };
      this.users.push(user);
      return [{ insertId: user.id }];
    }

    if (normalized.includes('select id, email, registered_at as createdat from users where id = :id')) {
      const user = this.users.find(item => item.id === params.id);
      return [[user ? { id: user.id, email: user.email, createdAt: user.created_at } : undefined].filter(Boolean)];
    }

    if (normalized.includes('delete from users where id = :userid')) {
      const before = this.users.length;
      this.users = this.users.filter(item => item.id !== params.userId);
      this.reminders = this.reminders.filter(item => item.user_id !== params.userId);
      return [{ affectedRows: before - this.users.length }];
    }

    if (normalized.includes('select id, user_id as userid')) {
      const rows = this.reminders
        .filter(item => item.user_id === params.userId)
        .map(item => ({
          id: item.id,
          userId: item.user_id,
          title: item.title,
          saleAt: item.sale_at,
          offsetsMinutes: JSON.stringify(item.offsets_minutes),
          enabled: item.enabled,
          createdAt: item.created_at,
          updatedAt: item.updated_at
        }));
      return [rows];
    }

    if (normalized.includes('insert into reminders')) {
      const reminder = {
        id: this.nextReminderId++,
        user_id: params.userId,
        title: params.title,
        sale_at: params.saleAt,
        offsets_minutes: JSON.parse(params.offsets),
        enabled: params.enabled,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      };
      this.reminders.push(reminder);
      return [{ insertId: reminder.id }];
    }

    if (normalized.includes('from reminders where id = :id')) {
      const reminder = this.reminders.find(item => item.id === params.id);
      return [[reminder ? {
        id: reminder.id,
        userId: reminder.user_id,
        title: reminder.title,
        saleAt: reminder.sale_at,
        offsetsMinutes: JSON.stringify(reminder.offsets_minutes),
        enabled: reminder.enabled,
        createdAt: reminder.created_at,
        updatedAt: reminder.updated_at
      } : undefined].filter(Boolean)];
    }

    if (normalized.includes('delete from reminders where id = :id and user_id = :userid')) {
      const before = this.reminders.length;
      this.reminders = this.reminders.filter(
        item => !(item.id === params.id && item.user_id === params.userId)
      );
      return [{ affectedRows: before - this.reminders.length }];
    }

    return [[]];
  }
}

async function withServer(fn) {
  const pool = new FakePool();
  const app = createApp({
    pool,
    authSecret: 'test-secret',
    now: () => 1_717_171_717
  });
  const server = app.listen(0);
  await new Promise(resolve => server.once('listening', resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    await fn({ baseUrl, pool });
  } finally {
    await new Promise((resolve, reject) => server.close(err => (err ? reject(err) : resolve())));
  }
}

test('registers, logs in, fetches profile, and deletes account', async () => {
  await withServer(async ({ baseUrl }) => {
    const registerRes = await fetch(`${baseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: 'friend@example.com',
        password: 'Secret123'
      })
    });

    assert.equal(registerRes.status, 201);
    const registerBody = await registerRes.json();
    assert.equal(registerBody.user.email, 'friend@example.com');
    assert.ok(registerBody.token);

    const loginRes = await fetch(`${baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: 'friend@example.com',
        password: 'Secret123'
      })
    });

    assert.equal(loginRes.status, 200);
    const loginBody = await loginRes.json();
    assert.ok(loginBody.token);

    const meRes = await fetch(`${baseUrl}/api/auth/me`, {
      headers: { Authorization: `Bearer ${loginBody.token}` }
    });
    assert.equal(meRes.status, 200);
    const meBody = await meRes.json();
    assert.equal(meBody.user.email, 'friend@example.com');

    const deleteRes = await fetch(`${baseUrl}/api/auth/me`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${loginBody.token}`
      },
      body: JSON.stringify({ password: 'Secret123' })
    });
    assert.equal(deleteRes.status, 200);
    const deleteBody = await deleteRes.json();
    assert.equal(deleteBody.ok, true);
  });
});

test('rejects duplicate registration and wrong password login', async () => {
  await withServer(async ({ baseUrl }) => {
    const payload = {
      email: 'friend@example.com',
      password: 'Secret123'
    };

    const firstRes = await fetch(`${baseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    assert.equal(firstRes.status, 201);

    const duplicateRes = await fetch(`${baseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    assert.equal(duplicateRes.status, 409);

    const badLoginRes = await fetch(`${baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: payload.email,
        password: 'WrongPassword'
      })
    });
    assert.equal(badLoginRes.status, 401);
  });
});

test('requires login for reminders', async () => {
  await withServer(async ({ baseUrl }) => {
    const reminderRes = await fetch(`${baseUrl}/api/reminders`);
    assert.equal(reminderRes.status, 401);
  });
});
