const express = require('express');
const pool = require('../config/db');
const router = express.Router();

// =========================================================================
// GET /api/parking/near?venueId=1&radius=500
// 🎯 核心升級：改用 venueId 數字進行主外鍵精確查詢，並動態計算 GPS 半徑距離
// =========================================================================
router.get('/near', async (req, res) => {
  try {
    // 1. 從 Query 參數中取得 venueId 與 radius，並做防禦性轉型
    const venueId = Number(req.query.venueId);
    const radius = Number(req.query.radius || 500); // 預設搜尋方圓 500 公尺內的停車場

    if (!venueId) {
      return res.status(400).json({ 
        error: 'missing_fields', 
        message: '請提供場館 venueId 數字參數' 
      });
    }

    // 2. 先從 venues 表用 ID 精確撈出該場館的名稱與 GPS 經緯度
    const [venueRows] = await pool.execute(
      'SELECT name, latitude, longitude FROM venues WHERE id = ? LIMIT 1',
      [venueId]
    );

    // 🛡️ 安全防禦：如果找不到場館，或是場館的經緯度沒有成功用 SQL 補上，直接回報前端
    if (!venueRows.length || !venueRows[0].latitude || !venueRows[0].longitude) {
      return res.status(404).json({ 
        error: 'venue_geo_not_found', 
        message: '找不到該場館或該場館尚未設定 GPS 座標' 
      });
    }

    const venueName = venueRows[0].name;
    const vLat = Number(venueRows[0].latitude);
    const vLng = Number(venueRows[0].longitude);

    // 3. 📐 核心空間演算法：利用哈弗辛公式 (Haversine Formula) 計算地球表面兩點直線距離
    // 同時對齊組員設計的欄位：fee (取代原本的 hourly_rate)、distance (文字距離)
    const [parkingRows] = await pool.execute(
      `
      SELECT 
        id,
        venue_id AS venueId,
        name,
        address,
        fee,           -- 🎯 對齊組員欄位
        status,
        distance AS staticDistance, -- 🎯 保留組員原本手動填的文字距離
        latitude,
        longitude,
        ROUND(
          6371000 * 2 * ASIN(
            SQRT(
              POWER(SIN((RADIANS(latitude) - RADIANS(?)) / 2), 2) +
              COS(RADIANS(?)) * COS(RADIANS(latitude)) *
              POWER(SIN((RADIANS(longitude) - RADIANS(?)) / 2), 2)
            )
          )
        ) AS dynamic_distance_meters -- 📐 後端演算法動態算出的精確公尺數
      FROM parking_lots
      WHERE venue_id = ? -- 🎯 效能優化：先用數字外鍵過濾，速度快上一倍
      HAVING dynamic_distance_meters <= ?
      ORDER BY dynamic_distance_meters ASC
      `,
      [vLat, vLat, vLng, venueId, radius]
    );

    // 4. 回傳完美對齊 Android 前端期待的乾淨 JSON 物件
    res.json({
      venueId: venueId,
      venueName: venueName,
      venue_coordinates: { latitude: vLat, longitude: vLng },
      search_radius_meters: radius,
      total: parkingRows.length,
      items: parkingRows
    });

  } catch (err) {
    console.error('GET /api/parking/near error:', err);
    res.status(500).json({ error: 'server_error', message: err.message });
  }
});

module.exports = router;