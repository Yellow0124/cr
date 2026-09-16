const express = require('express');

// Count activities once per event, and venues using the same names as the guide.
const SUMMARY_SQL = `
  SELECT
    ((SELECT COUNT(*) FROM defaultdb.events) +
     (SELECT COUNT(*) FROM defaultdb.opendata_culture_events)) AS events,
    (SELECT COUNT(*) FROM defaultdb.artists) AS artists,
    (SELECT COUNT(*) FROM (
      SELECT name FROM defaultdb.venues WHERE name IS NOT NULL AND name <> ''
      UNION
      SELECT location_name AS name FROM defaultdb.opendata_culture_event_shows
      WHERE location_name IS NOT NULL AND location_name <> ''
    ) AS combined_venues) AS venues,
    (SELECT COUNT(*) FROM defaultdb.reminders WHERE user_id = ?) AS reminders
`;

module.exports = function createHomeStatsRouter({ pool, requireAuth }) {
  const router = express.Router();
  router.get('/', requireAuth, async (req, res) => {
    try {
      const [[row]] = await pool.execute(SUMMARY_SQL, [req.user.id]);
      res.set('Cache-Control', 'no-store');
      res.json(Object.fromEntries(['events', 'artists', 'venues', 'reminders']
        .map(key => [key, Number(row[key])])));
    } catch (error) {
      console.error('GET /api/stats/home error:', error);
      res.status(500).json({ error: 'stats_unavailable' });
    }
  });
  return router;
};
