const express = require('express');
const pool = require('../config/db');

const router = express.Router();

// 1. GET /summary - 首頁儀表板四大核心卡片與最新活動
router.get('/summary', async (_req, res) => {
  try {
    const [[events]] = await pool.query('SELECT COUNT(*) AS total FROM events');
    const [[artists]] = await pool.query('SELECT COUNT(*) AS total FROM artists');
    const [[venues]] = await pool.query('SELECT COUNT(*) AS total FROM venues');
    
    let remindersCount = 0;
    try {
      const [[reminders]] = await pool.query('SELECT COUNT(*) AS total FROM reminders');
      remindersCount = reminders.total;
    } catch (e) {
      console.warn('⚠️ reminders 表可能尚不存在，計數暫代為 0');
    }

    const [latest] = await pool.query(`
      SELECT 
        event_id AS id, 
        COALESCE(event_name, '未命名活動') AS title, 
        COALESCE(DATE_FORMAT(event_time, '%Y-%m-%d %H:%i:%s'), '') AS activityTime, 
        COALESCE(venue_name, '未提供場地') AS venue
      FROM v_all_events_summary
      ORDER BY scraped_at DESC, event_id DESC
      LIMIT 5
    `);

    res.json({
      events: Number(events.total || 0),
      artists: Number(artists.total || 0),
      venues: Number(venues.total || 0),
      reminders: Number(remindersCount || 0),
      latest
    });
  } catch (err) {
    console.error('GET /api/stats/summary error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

// 2. GET /price - 票價區間看板分析
router.get('/price', async (_req, res) => {
  try {
    const [rows] = await pool.query(`
      SELECT 
        et.event_id AS id, 
        e.name AS title, 
        et.price AS price
      FROM event_tickets et
      JOIN events e ON et.event_id = e.id
      WHERE et.price IS NOT NULL
    `);

    const buckets = {
      freeOrUnknown: 0,
      under1000: 0,
      between1000And3000: 0,
      between3000And6000: 0,
      over6000: 0
    };

    let totalSum = 0;
    const events = rows.map(row => {
      const p = Number(row.price);
      totalSum += p;

      if (p === 0) buckets.freeOrUnknown += 1;
      else if (p < 1000) buckets.under1000 += 1;
      else if (p < 3000) buckets.between1000And3000 += 1;
      else if (p < 6000) buckets.between3000And6000 += 1;
      else buckets.over6000 += 1;

      return {
        id: row.id,
        title: row.title,
        price: row.price,
        minPrice: p,
        maxPrice: p
      };
    });

    const averageMaxPrice = rows.length ? Math.round(totalSum / rows.length) : 0;

    res.json({
      total: rows.length,
      priced: rows.length,
      averageMaxPrice,
      buckets,
      topExpensive: events
        .sort((a, b) => b.maxPrice - a.maxPrice)
        .slice(0, 10)
    });
  } catch (err) {
    console.error('GET /api/stats/price error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

// 3. GET /time - 熱門搶票與活動月份 analysis
router.get('/time', async (_req, res) => {
  try {
    const [monthRows] = await pool.query(`
      SELECT 
        DATE_FORMAT(event_time, '%Y.%m') AS month, 
        COUNT(*) AS total
      FROM events
      WHERE event_time IS NOT NULL
      GROUP BY DATE_FORMAT(event_time, '%Y.%m')
      ORDER BY month ASC
    `);

    const busiestMonths = [...monthRows]
      .sort((a, b) => b.total - a.total)
      .slice(0, 6);

    const [totalRows] = await pool.query('SELECT COUNT(*) AS total FROM events WHERE event_time IS NOT NULL');

    res.json({
      total: totalRows[0].total,
      months: monthRows,
      busiestMonths
    });
  } catch (err) {
    console.error('GET /api/stats/time error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

// 4. GET /venue - 合作熱門場館排行統計
router.get('/venue', async (_req, res) => {
  try {
    const [rows] = await pool.query(`
      SELECT 
        COALESCE(v.name, '未提供場地') AS venue, 
        COUNT(*) AS total
      FROM events e
      LEFT JOIN venues v ON e.venue_id = v.id
      GROUP BY v.id, v.name
      ORDER BY total DESC
      LIMIT 15
    `);

    res.json({ venues: rows });
  } catch (err) {
    console.error('GET /api/stats/venue error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

module.exports = router;