const express = require('express');

function createRemindersRouter({ pool, requireAuth }) {
  const router = express.Router();

  // ==========================================
  // 1. GET /api/reminders - 讀取當前使用者的提醒資料
  // ==========================================
  router.get('/', requireAuth, async (req, res) => {
    try {
      const [rows] = await pool.execute(
        `
        SELECT
          r.id,
          r.user_id AS userId,
          COALESCE(e.event_name, '精選售票活動') AS title,
          COALESCE(DATE_FORMAT(r.reminded_at, '%Y-%m-%d %H:%i:%s'), '') AS saleAt,
          COALESCE(TIMESTAMPDIFF(MINUTE, r.reminded_at, e.ticket_sale_time), 0) AS offsetsMinutes,
          1 AS enabled,
          COALESCE(DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s'), '') AS createdAt,
          COALESCE(DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s'), '') AS updatedAt
        FROM reminders r
        LEFT JOIN v_all_events_summary e ON r.event_id = e.event_id
        WHERE r.user_id = ?
        ORDER BY r.reminded_at ASC, r.id DESC
        `,
        [req.user.id]
      );

      res.json(rows);
    } catch (err) {
      console.error('GET /api/reminders error:', err);
      res.status(500).json({ error: 'server_error', message: err.message });
    }
  });

  // ==========================================
  // 2. POST /api/reminders - 新增搶票提醒
  // ==========================================
  router.post('/', requireAuth, async (req, res) => {
    try {
      const { eventId, event_id, remindedAt, reminded_at, saleAt, sale_at, daysBefore, hoursBefore, minutesBefore } = req.body;
      
      // 彈性相容 Android 端傳過來的各種命名蛇形或駝峰命名
      const finalEventId = Number(eventId || event_id || 1);
      let finalRemindedAt = String(remindedAt || reminded_at || '').trim();
      if (!finalRemindedAt) {
        const saleText = String(saleAt || sale_at || '').trim();
        const saleDate = saleText ? new Date(saleText) : null;
        if (saleDate && Number.isFinite(saleDate.getTime())) {
          const offsetMinutes = normalizeOffsetMinutes(daysBefore, hoursBefore, minutesBefore);
          finalRemindedAt = formatMysqlDate(new Date(saleDate.getTime() - offsetMinutes * 60 * 1000));
        }
      }

      if (!finalRemindedAt) {
        return res.status(400).json({
          error: 'missing_fields',
          need: ['remindedAt']
        });
      }

      const [result] = await pool.execute(
        `
        INSERT INTO reminders (user_id, event_id, reminded_at)
        VALUES (?, ?, ?)
        `,
        [req.user.id, finalEventId, finalRemindedAt]
      );

      const [rows] = await pool.execute(
        `
        SELECT
          r.id,
          r.user_id AS userId,
          COALESCE(e.event_name, '精選售票活動') AS title,
          COALESCE(DATE_FORMAT(r.reminded_at, '%Y-%m-%d %H:%i:%s'), '') AS saleAt,
          COALESCE(TIMESTAMPDIFF(MINUTE, r.reminded_at, e.ticket_sale_time), 0) AS offsetsMinutes,
          1 AS enabled,
          COALESCE(DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s'), '') AS createdAt
        FROM reminders r
        LEFT JOIN v_all_events_summary e ON r.event_id = e.event_id
        WHERE r.id = ?
        `,
        [result.insertId]
      );

      res.status(201).json(rows[0]);
    } catch (err) {
      console.error('POST /api/reminders error:', err);
      res.status(500).json({ error: 'server_error', message: err.message });
    }
  });

  // ==========================================
  // 3. DELETE /api/reminders/:id - 刪除搶票提醒
  // ==========================================
  router.delete('/:id', requireAuth, async (req, res) => {
    try {
      const [result] = await pool.execute(
        `DELETE FROM reminders WHERE id = ? AND user_id = ?`,
        [Number(req.params.id), req.user.id]
      );

      res.json({ ok: true, deleted: result.affectedRows });
    } catch (err) {
      console.error('DELETE /api/reminders/:id error:', err);
      res.status(500).json({ error: 'server_error', message: err.message });
    }
  });

  return router;
}

function normalizeOffsetMinutes(days, hours, minutes) {
  const d = Math.max(Math.min(Number(days || 0), 7), 0);
  const h = Math.max(Math.min(Number(hours || 0), 23), 0);
  const m = Math.max(Math.min(Number(minutes || 0), 59), 0);
  return Math.round(d) * 1440 + Math.round(h) * 60 + Math.round(m);
}

function formatMysqlDate(date) {
  const pad = value => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

module.exports = createRemindersRouter;
