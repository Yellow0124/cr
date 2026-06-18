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

router.get('/count', async (_req, res) => {
  try {
    const [[row]] = await pool.query('SELECT COUNT(*) AS total FROM defaultdb.events');
    res.json({ total: Number(row.total || 0) });
  } catch (err) {
    console.error('GET /api/events/count error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

router.get('/random', async (req, res) => {
  try {
    const limit = Math.max(Math.min(Number(req.query.limit || 5), 20), 1);
    const [rows] = await pool.query(
      `
      SELECT
        e.id,
        e.name AS title,
        COALESCE(v.name, '') AS venue,
        COALESCE(v.address, '') AS address,
        COALESCE(DATE_FORMAT(e.ticket_sale_time, '%Y-%m-%d %H:%i:%s'), '') AS saleTime,
        COALESCE(DATE_FORMAT(e.event_time, '%Y-%m-%d %H:%i:%s'), '') AS activityTime,
        COALESCE(e.source_site, '') AS source,
        COALESCE(e.source_website, '') AS ticketType,
        COALESCE(e.event_url, '') AS url,
        COALESCE(s.artists_summary, '') AS artist,
        COALESCE(s.tickets_summary, '') AS price
      FROM defaultdb.events e
      LEFT JOIN defaultdb.venues v ON e.venue_id = v.id
      LEFT JOIN defaultdb.v_all_events_summary s ON s.event_id = e.id
      WHERE e.name IS NOT NULL AND e.name <> ''
      ORDER BY RAND()
      LIMIT ${Number.parseInt(limit, 10)}
      `
    );
    res.json({ total: rows.length, items: rows });
  } catch (err) {
    console.error('GET /api/events/random error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

router.get('/:id/artist-profile', async (req, res) => {
  try {
    const eventId = Number(req.params.id);
    const [[eventRow]] = await pool.execute(
      `
      SELECT
        COALESCE(s.artists_summary, '') AS artistsSummary,
        COALESCE(e.name, '') AS eventName
      FROM defaultdb.events e
      LEFT JOIN defaultdb.v_all_events_summary s ON s.event_id = e.id
      WHERE e.id = ?
      LIMIT 1
      `,
      [eventId]
    );

    const terms = String(eventRow?.artistsSummary || eventRow?.eventName || '')
      .split(/[、,，/／|]/)
      .map(value => value.trim())
      .filter(Boolean);
    if (!terms.length) return res.json({ artist: null, news: [], categories: [] });

    const where = terms.map(() => 'a.name LIKE ?').join(' OR ');
    const params = terms.map(term => `%${term}%`);
    const [artistRows] = await pool.execute(
      `
      SELECT a.id, a.name, a.wiki_intro AS wikiIntro, a.wiki_url AS wikiUrl
      FROM defaultdb.artists a
      WHERE ${where}
      ORDER BY CHAR_LENGTH(a.name) DESC
      LIMIT 1
      `,
      params
    );
    const artist = artistRows[0] || null;
    if (!artist) return res.json({ artist: null, news: [], categories: [] });

    const [news] = await pool.execute(
      `
      SELECT title, url, COALESCE(DATE_FORMAT(published_at, '%Y-%m-%d'), '') AS publishedAt
      FROM defaultdb.artist_news
      WHERE artist_id = ?
      ORDER BY published_at DESC, id DESC
      LIMIT 5
      `,
      [artist.id]
    );
    const [categories] = await pool.execute(
      `
      SELECT genre, language
      FROM defaultdb.artist_categories
      WHERE artist_id = ?
      ORDER BY id ASC
      `,
      [artist.id]
    );
    res.json({ artist, news, categories });
  } catch (err) {
    console.error('GET /api/events/:id/artist-profile error:', err);
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
