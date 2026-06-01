const express = require('express');
const pool = require('../config/db');

const router = express.Router();

const eventFields = `
  event_id AS id,
  COALESCE(event_name, '未命名活動') AS title,
  '' AS artist,                                                    -- 🛡️ 防禦
  COALESCE(DATE_FORMAT(ticket_sale_time, '%Y-%m-%d %H:%i:%s'), '') AS saleTime,
  COALESCE(DATE_FORMAT(event_time, '%Y-%m-%d %H:%i:%s'), '') AS activityTime,
  COALESCE(venue_name, '未提供場地') AS venue,
  COALESCE(venue_address, '') AS address,
  '' AS price,                                                     -- 🛡️ 防禦
  '尚未公布' AS ticketType,                                        -- 🛡️ 核心修正：直接給固定字串，完美避開 genres 欄位噴錯！
  COALESCE(event_url, '') AS url,
  COALESCE(source_site, '') AS source,
  COALESCE(scraped_at, '') AS scrapedAt
`;

function buildEventWhere(query) {
  const conditions = [];
  const params = [];
  const keyword = String(query.keyword || query.q || '').trim();
  const venue = String(query.venue || query.location || '').trim();
  const source = String(query.source || '').trim();
  const startDate = String(query.startDate || '').trim();
  const endDate = String(query.endDate || '').trim();
  const featured = String(query.featured || '').trim() === '1';

  if (featured) {
    conditions.push(`(
      event_name IS NOT NULL AND
      event_name <> '' AND
      event_name NOT LIKE '%Tickets in Japan%' AND
      (
        event_time IS NOT NULL OR
        ticket_sale_time IS NOT NULL OR
        (venue_name IS NOT NULL AND venue_name <> '' AND venue_name <> '未提供')
      )
    )`);
  }

  if (keyword) {
    conditions.push(`(
      event_name LIKE ? OR
      venue_name LIKE ? OR
      venue_address LIKE ? OR
      source_site LIKE ?
    )`);
    const kwParam = `%${keyword}%`;
    params.push(kwParam, kwParam, kwParam, kwParam);
  }

  if (venue && venue !== '全部') {
    conditions.push(`(venue_name LIKE ? OR venue_address LIKE ?)`);
    const venueParam = `%${venue}%`;
    params.push(venueParam, venueParam);
  }

  if (source && source !== '全部') {
    conditions.push(`source_site LIKE ?`);
    params.push(`%${source}%`);
  }

  if (startDate && endDate) {
    conditions.push(`event_time BETWEEN ? AND ?`);
    params.push(startDate, endDate);
  }

  return {
    whereSql: conditions.length ? `WHERE ${conditions.join(' AND ')}` : '',
    params
  };
}

router.get('/', async (req, res) => {
  try {
    const rawLimit = Number(req.query.limit || 100);
    const rawOffset = Number(req.query.offset || 0);
    
    const limit = Math.max(Math.min(rawLimit, 300), 1);
    const offset = Math.max(rawOffset, 0);
    
    const { whereSql, params } = buildEventWhere(req.query);

    const selectParams = [];
    for (let i = 0; i < params.length; i++) {
      selectParams.push(String(params[i]));
    }

    const [rows] = await pool.execute(
      `
      SELECT ${eventFields}
      FROM v_all_events_summary
      ${whereSql}
      ORDER BY event_id DESC
      LIMIT ${Number.parseInt(limit, 10)} OFFSET ${Number.parseInt(offset, 10)}
      `,
      selectParams
    );

    const [countRows] = await pool.execute(
      `SELECT COUNT(*) AS total FROM v_all_events_summary ${whereSql}`,
      selectParams
    );

    res.json({
      total: Number(countRows[0]?.total || 0),
      items: rows
    });
  } catch (err) {
    console.error('GET /api/events error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

router.get('/meta', async (_req, res) => {
  try {
    const [venues] = await pool.query(`
      SELECT name AS venue, COUNT(*) AS total
      FROM venues
      WHERE name IS NOT NULL AND name <> ''
      GROUP BY name
      ORDER BY total DESC
      LIMIT 30
    `);

    const [sources] = await pool.query(`
      SELECT source_site AS source, COUNT(*) AS total
      FROM events
      WHERE source_site IS NOT NULL AND source_site <> ''
      GROUP BY source_site
      ORDER BY total DESC
      LIMIT 20
    `);

    res.json({ venues, sources });
  } catch (err) {
    console.error('GET /api/events/meta error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

router.get('/:id', async (req, res) => {
  try {
    const eventId = Number(req.params.id);
    const [rows] = await pool.execute(
      `SELECT ${eventFields} FROM v_all_events_summary WHERE event_id = ? LIMIT 1`,
      [eventId]
    );

    if (!rows.length) {
      return res.status(404).json({ error: 'not_found' });
    }

    res.json(rows[0]);
  } catch (err) {
    console.error('GET /api/events/:id error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

module.exports = router;