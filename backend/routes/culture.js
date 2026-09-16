const express = require('express');
module.exports = function createCultureRouter({ pool }) {

const router = express.Router();

function pageSize(value, fallback = 50) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? Math.max(1, Math.min(parsed, 300)) : fallback;
}

router.get('/events', async (req, res) => {
  try {
    const limit = pageSize(req.query.limit);
    const offset = Math.max(0, Number.parseInt(req.query.offset, 10) || 0);
    const keyword = String(req.query.keyword || '').trim();
    const pattern = `%${keyword}%`;
    const [rows] = await pool.execute(`
      SELECT e.uid, CONCAT('culture:', e.uid, ':', COALESCE(s.id, 0)) AS itemId, COALESCE(e.title, '') AS title,
             COALESCE(e.description, '') AS description,
             COALESCE(e.image_url, '') AS imageUrl,
             COALESCE(e.web_sales, '') AS url,
             COALESCE(e.source_web_name, '') AS source,
             COALESCE(NULLIF(s.show_time, ''), e.start_date, '') AS showTime,
             COALESCE(s.location_name, '') AS venue,
             COALESCE(s.address, '') AS address,
             COALESCE(s.price, '') AS price
      FROM defaultdb.opendata_culture_events e
      LEFT JOIN defaultdb.opendata_culture_event_shows s ON s.event_uid = e.uid
      WHERE e.title IS NOT NULL AND e.title <> ''
        AND (? = '' OR e.title LIKE ? OR s.location_name LIKE ? OR s.address LIKE ?)
      ORDER BY e.updated_at DESC, e.uid DESC, s.show_time, s.id
      LIMIT ${limit} OFFSET ${offset}
    `, [keyword, pattern, pattern, pattern]);
    for (const row of rows) {
      row.imageUrl = row.imageUrl.replace('https://cloud.culture.twhttps://cloud.culture.tw', 'https://cloud.culture.tw');
    }
    res.json({ total: rows.length, items: rows });
  } catch (err) {
    console.error('GET /api/culture/events error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

router.get('/venues', async (req, res) => {
  try {
    const limit = pageSize(req.query.limit, 100);
    const offset = Math.max(0, Number.parseInt(req.query.offset, 10) || 0);
    const [rows] = await pool.query(`
      SELECT MIN(id) AS id, name,
             COALESCE(MAX(NULLIF(address, '')), '') AS address,
             COALESCE(MAX(NULLIF(city, '')), '') AS city,
             COALESCE(MAX(CASE WHEN source = 'venue' THEN NULLIF(description, '') END),
                      MAX(NULLIF(description, '')), '') AS description
      FROM (
        SELECT id, name, address, city, description, 'venue' AS source
        FROM defaultdb.venues
        WHERE name IS NOT NULL AND name <> ''
        UNION ALL
        SELECT MIN(id) AS id, location_name AS name,
               COALESCE(MAX(NULLIF(address, '')), '') AS address,
               '' AS city,
               CONCAT('政府開放資料，共 ', COUNT(*), ' 筆活動場次') AS description,
               'culture' AS source
        FROM defaultdb.opendata_culture_event_shows
        WHERE location_name IS NOT NULL AND location_name <> ''
        GROUP BY location_name
      ) AS venue_sources
      GROUP BY name
      ORDER BY name
      LIMIT ${limit} OFFSET ${offset}
    `);
    res.json({ total: rows.length, items: rows });
  } catch (err) {
    console.error('GET /api/culture/venues error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

return router;
};
