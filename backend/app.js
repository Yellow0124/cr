const express = require('express');
const cors = require('cors');

const defaultPool = require('./config/db');
const eventsRoute = require('./routes/events');
const createAuthRouter = require('./routes/auth');
const createRemindersRouter = require('./routes/reminders');
const statsRoute = require('./routes/stats');
const parkingRouter = require('./routes/parking');
const createRecommendationsRouter = require('./routes/recommendations');
const venuesRouter = require('./routes/venues');
const { authMiddleware } = require('./auth');


function createApp(options = {}) {
  const pool = options.pool || defaultPool;
  const authSecret = options.authSecret || process.env.JWT_SECRET || 'change-me';
  const now = options.now;
  const requireAuth = authMiddleware({ pool, secret: authSecret, now });

  const app = express();

  app.use(cors());
  app.use(express.json());

  app.get('/', (_req, res) => {
    res.json({
      name: 'Ticket API',
      status: 'running',
      endpoints: [
        '/api/auth/register',
        '/api/auth/login',
        '/api/auth/me',
        '/api/events',
        '/api/events/meta',
        '/api/venues',
        '/api/reminders',
        '/api/stats/summary',
        '/api/stats/price',
        '/api/stats/time',
        '/api/stats/venue'
      ]
    });
  });

  app.get('/api/health', async (_req, res) => {
    try {
      const [[row]] = await pool.query('SELECT 1 AS ok');
      res.json({ ok: row.ok === 1 });
    } catch (err) {
      res.status(500).json({ ok: false, message: err.message });
    }
  });

  app.get('/api/health/db', async (_req, res) => {
    try {
      const [[row]] = await pool.query('SELECT COUNT(*) AS eventCount FROM defaultdb.events');
      res.json({ ok: true, eventCount: Number(row.eventCount || 0), database: 'defaultdb', table: 'events' });
    } catch (err) {
      res.status(500).json({ ok: false, error: 'db_connection_failed', message: err.message });
    }
  });

  app.use('/api/auth', createAuthRouter({ pool, secret: authSecret, now, requireAuth }));
  app.use('/api/events', eventsRoute);
  app.use('/api/reminders', createRemindersRouter({ pool, requireAuth }));
  app.use('/api/recommendations', createRecommendationsRouter({ pool, requireAuth }));
  app.use('/api/stats', statsRoute);
  app.use('/api/parking', parkingRouter);
  app.use('/api/venues', venuesRouter);

  app.use((req, res) => {
    res.status(404).json({ error: 'not_found', path: req.path });
  });

  return app;
}

module.exports = { createApp };
