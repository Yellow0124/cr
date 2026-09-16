const express = require('express');
const pool = require('../config/db');

const router = express.Router();

router.get('/', async (req, res) => {
  try {
    const limit = Math.max(Math.min(Number(req.query.limit || 100), 300), 1);
    const [rows] = await pool.query(
      `
      SELECT
        id,
        COALESCE(name, '') AS name,
        COALESCE(address, '') AS address,
        COALESCE(city, '') AS city,
        COALESCE(description, '') AS description
      FROM defaultdb.venues
      WHERE name IS NOT NULL AND name <> ''
      ORDER BY name ASC
      LIMIT ${Number.parseInt(limit, 10)}
      `
    );
    res.json({ total: rows.length, items: rows });
  } catch (err) {
    console.error('GET /api/venues error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

router.get('/:id', async (req, res) => {
  try {
    const venueId = Number(req.params.id);
    const [rows] = await pool.execute(
      `
      SELECT
        id,
        COALESCE(name, '') AS name,
        COALESCE(address, '') AS address,
        COALESCE(city, '') AS city,
        COALESCE(description, '') AS description,
        latitude,
        longitude
      FROM defaultdb.venues
      WHERE id = ?
      LIMIT 1
      `,
      [venueId]
    );
    if (!rows.length) return res.status(404).json({ error: 'not_found' });
    res.json(rows[0]);
  } catch (err) {
    console.error('GET /api/venues/:id error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

module.exports = router;
