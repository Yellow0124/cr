const mysql = require('mysql2/promise');
require('dotenv').config();

const sslEnabled = String(process.env.DB_SSL || 'true').toLowerCase() !== 'false';

const pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  ssl: sslEnabled ? { rejectUnauthorized: false } : undefined,
  waitForConnections: true,
  connectTimeout: Number(process.env.DB_CONNECT_TIMEOUT_MS || 8000),
  connectionLimit: 10,
  namedPlaceholders: true
});

module.exports = pool;
