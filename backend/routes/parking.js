const express = require('express');
const axios = require('axios');
const pool = require('../config/db');

const router = express.Router();

const fallbackVenues = [
  { id: 1, name: '台北小巨蛋', latitude: 25.051494, longitude: 121.549506 },
  { id: 2, name: '台北大巨蛋', latitude: 25.04392, longitude: 121.560328 },
  { id: 3, name: 'Zepp New Taipei', latitude: 25.061197, longitude: 121.453706 },
  { id: 4, name: 'Legacy Taipei', latitude: 25.044279, longitude: 121.529318 },
  { id: 5, name: '高雄流行音樂中心', latitude: 22.620737, longitude: 120.286757 }
];

const fallbackParkingLots = [
  { id: 101, venueId: 1, name: '台北小巨蛋地下停車場', address: '台北市松山區南京東路四段2號', fee: '每小時 60 元', status: '尚有空位', latitude: 25.051032, longitude: 121.549513 },
  { id: 102, venueId: 1, name: '北寧路立體停車場', address: '台北市松山區北寧路32巷', fee: '每小時 40 元', status: '尚有空位', latitude: 25.049905, longitude: 121.552852 },
  { id: 103, venueId: 1, name: '南京東路四段停車場', address: '台北市松山區南京東路四段', fee: '每小時 50 元', status: '接近滿位', latitude: 25.052742, longitude: 121.546253 },
  { id: 201, venueId: 2, name: '台北大巨蛋停車場', address: '台北市信義區忠孝東路四段515號', fee: '每小時 60 元', status: '尚有空位', latitude: 25.043604, longitude: 121.560017 },
  { id: 202, venueId: 2, name: '松菸地下停車場', address: '台北市信義區光復南路133號', fee: '每小時 50 元', status: '尚有空位', latitude: 25.044671, longitude: 121.559309 },
  { id: 301, venueId: 3, name: '新莊宏匯廣場停車場', address: '新北市新莊區新北大道四段3號', fee: '每小時 50 元', status: '尚有空位', latitude: 25.060985, longitude: 121.453062 },
  { id: 302, venueId: 3, name: '新北產業園區站停車場', address: '新北市新莊區五工路', fee: '每小時 30 元', status: '尚有空位', latitude: 25.061762, longitude: 121.459501 },
  { id: 401, venueId: 4, name: '華山文創園區停車場', address: '台北市中正區八德路一段1號', fee: '每小時 50 元', status: '尚有空位', latitude: 25.043852, longitude: 121.529586 },
  { id: 501, venueId: 5, name: '海音館停車場', address: '高雄市鹽埕區真愛路1號', fee: '每小時 40 元', status: '尚有空位', latitude: 22.620228, longitude: 120.286529 }
];

function haversineMeters(aLat, aLng, bLat, bLng) {
  const toRad = value => (value * Math.PI) / 180;
  const dLat = toRad(bLat - aLat);
  const dLng = toRad(bLng - aLng);
  const lat1 = toRad(aLat);
  const lat2 = toRad(bLat);
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return Math.round(6371000 * 2 * Math.asin(Math.sqrt(h)));
}

function fallbackResponse(venueId, radius) {
  const venue = fallbackVenues.find(item => item.id === venueId) || fallbackVenues[0];
  const items = fallbackParkingLots
    .filter(item => item.venueId === venue.id)
    .map(item => {
      const distance = haversineMeters(venue.latitude, venue.longitude, item.latitude, item.longitude);
      return {
        ...item,
        staticDistance: `${distance}m`,
        dynamic_distance_meters: distance
      };
    })
    .filter(item => item.dynamic_distance_meters <= radius)
    .sort((a, b) => a.dynamic_distance_meters - b.dynamic_distance_meters);

  return {
    venueId: venue.id,
    venueName: venue.name,
    venue_coordinates: { latitude: venue.latitude, longitude: venue.longitude },
    search_radius_meters: radius,
    source: 'fallback',
    total: items.length,
    items
  };
}

function fallbackLocationResponse(lat, lng, radius) {
  const items = fallbackParkingLots
    .map(item => {
      const distance = haversineMeters(lat, lng, item.latitude, item.longitude);
      return {
        ...item,
        staticDistance: `${distance}m`,
        dynamic_distance_meters: distance
      };
    })
    .filter(item => item.dynamic_distance_meters <= radius)
    .sort((a, b) => a.dynamic_distance_meters - b.dynamic_distance_meters);

  return {
    venueId: null,
    venueName: '目前位置',
    venue_coordinates: { latitude: lat, longitude: lng },
    search_radius_meters: radius,
    source: 'fallback',
    total: items.length,
    items
  };
}

function fallbackGeocodeAddress(address) {
  const normalized = String(address || '').toLowerCase();
  const aliases = [
    { keywords: ['小巨蛋', 'taipei arena'], venueId: 1 },
    { keywords: ['大巨蛋', 'taipei dome'], venueId: 2 },
    { keywords: ['zepp'], venueId: 3 },
    { keywords: ['legacy', '華山'], venueId: 4 },
    { keywords: ['高雄', '海音', '流行音樂中心'], venueId: 5 }
  ];

  const match = aliases.find(item =>
    item.keywords.some(keyword => normalized.includes(keyword.toLowerCase()))
  );
  return fallbackVenues.find(item => item.id === match?.venueId) || null;
}

async function geocodeAddress(address) {
  const apiKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!apiKey) {
    throw new Error('GOOGLE_MAPS_API_KEY is not configured');
  }

  const response = await axios.get('https://maps.googleapis.com/maps/api/geocode/json', {
    params: {
      address,
      key: apiKey,
      region: 'tw',
      language: 'zh-TW'
    },
    timeout: Number(process.env.GOOGLE_MAPS_TIMEOUT_MS || 8000)
  });

  if (response.data?.status !== 'OK' || !response.data.results?.length) {
    throw new Error(`Google geocoding failed: ${response.data?.status || 'NO_RESULT'}`);
  }

  const location = response.data.results[0].geometry.location;
  return {
    latitude: Number(location.lat),
    longitude: Number(location.lng),
    formattedAddress: response.data.results[0].formatted_address
  };
}

router.get('/near', async (req, res) => {
  const venueId = Number(req.query.venueId);
  const lat = Number(req.query.lat);
  const lng = Number(req.query.lng);
  const address = String(req.query.address || '').trim();
  const radius = Math.max(Number(req.query.radius || 500), 100);
  const hasCoordinates = Number.isFinite(lat) && Number.isFinite(lng);
  const hasAddress = address.length > 0;

  if (!venueId && !hasCoordinates && !hasAddress) {
    return res.status(400).json({
      error: 'missing_fields',
      message: 'venueId, lat/lng, or address is required'
    });
  }

  try {
    if (hasAddress) {
      const geocoded = await geocodeAddress(address);
      const [parkingRows] = await pool.execute(
        `
        SELECT
          id,
          venue_id AS venueId,
          name,
          address,
          fee,
          status,
          distance AS staticDistance,
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
          ) AS dynamic_distance_meters
        FROM parking_lots
        HAVING dynamic_distance_meters <= ?
        ORDER BY dynamic_distance_meters ASC
        `,
        [geocoded.latitude, geocoded.latitude, geocoded.longitude, radius]
      );

      return res.json({
        venueId: null,
        venueName: geocoded.formattedAddress || address,
        venue_coordinates: { latitude: geocoded.latitude, longitude: geocoded.longitude },
        search_radius_meters: radius,
        source: 'google-geocoding',
        total: parkingRows.length,
        items: parkingRows
      });
    }

    if (hasCoordinates) {
      const [parkingRows] = await pool.execute(
        `
        SELECT
          id,
          venue_id AS venueId,
          name,
          address,
          fee,
          status,
          distance AS staticDistance,
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
          ) AS dynamic_distance_meters
        FROM parking_lots
        HAVING dynamic_distance_meters <= ?
        ORDER BY dynamic_distance_meters ASC
        `,
        [lat, lat, lng, radius]
      );

      return res.json({
        venueId: null,
        venueName: '目前位置',
        venue_coordinates: { latitude: lat, longitude: lng },
        search_radius_meters: radius,
        source: 'database',
        total: parkingRows.length,
        items: parkingRows
      });
    }

    const [venueRows] = await pool.execute(
      'SELECT name, latitude, longitude FROM venues WHERE id = ? LIMIT 1',
      [venueId]
    );

    if (!venueRows.length || !venueRows[0].latitude || !venueRows[0].longitude) {
      return res.json(fallbackResponse(venueId, radius));
    }

    const venueName = venueRows[0].name;
    const vLat = Number(venueRows[0].latitude);
    const vLng = Number(venueRows[0].longitude);

    const [parkingRows] = await pool.execute(
      `
      SELECT
        id,
        venue_id AS venueId,
        name,
        address,
        fee,
        status,
        distance AS staticDistance,
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
        ) AS dynamic_distance_meters
      FROM parking_lots
      WHERE venue_id = ?
      HAVING dynamic_distance_meters <= ?
      ORDER BY dynamic_distance_meters ASC
      `,
      [vLat, vLat, vLng, venueId, radius]
    );

    res.json({
      venueId,
      venueName,
      venue_coordinates: { latitude: vLat, longitude: vLng },
      search_radius_meters: radius,
      source: 'database',
      total: parkingRows.length,
      items: parkingRows
    });
  } catch (err) {
    console.warn('GET /api/parking/near fallback:', err.message);
    if (hasAddress) {
      const venue = fallbackGeocodeAddress(address);
      if (venue) {
        return res.json(fallbackResponse(venue.id, radius));
      }
      return res.status(502).json({
        error: 'geocoding_failed',
        message: err.message
      });
    }
    if (hasCoordinates) {
      return res.json(fallbackLocationResponse(lat, lng, radius));
    }
    res.json(fallbackResponse(venueId, radius));
  }
});

module.exports = router;
