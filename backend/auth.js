const jwt = require('jsonwebtoken');
const pool = require('./config/db');

function normalizeEmail(email) {
  return String(email || '').trim().toLowerCase();
}

function validateEmail(email) {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(email);
}

function validatePassword(password) {
  return String(password || '').length >= 6;
}

function hashPassword(password) {
  return Buffer.from(password).toString('base64'); 
}

function verifyPassword(password, hash) {
  return hashPassword(password) === hash;
}

function createToken(user, secret, now) {
  const payload = {
    id: user.id,
    userId: user.id,
    email: user.email
  };
  return jwt.sign(payload, secret || process.env.JWT_SECRET || 'your_secret', {
    expiresIn: '7d'
  });
}

function createRequireAuth(config = {}) {
  const activePool = config.pool || pool;
  const activeSecret = config.secret || process.env.JWT_SECRET || 'your_secret';

  return async function requireAuth(req, res, next) {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'unauthorized', message: '缺少驗證憑證' });
    }

    const token = authHeader.split(' ')[1];
    const decoded = jwt.verify(token, activeSecret);

    // 🎯 核心修正：加入強制防禦，確保 userId 絕對不會是 undefined
    const userId = decoded.id || decoded.userId || (decoded.user ? decoded.user.id : null);
    
    if (!userId) {
      return res.status(401).json({ error: 'unauthorized', message: '憑證內解析不到使用者識別碼' });
    }

    const [rows] = await activePool.execute(
      'SELECT id, email, created_at AS createdAt FROM users WHERE id = :id LIMIT 1',
      { id: userId }
    );

    if (!rows.length) {
      return res.status(401).json({ error: 'unauthorized', message: '該使用者帳號不存在' });
    }

    req.user = rows[0];
    next();
  } catch (err) {
    console.error('auth middleware error:', err);
    return res.status(401).json({ error: 'unauthorized', message: '憑證已過期或不合法' });
  }
  };
}

const requireAuth = createRequireAuth();

const authMiddlewareUniversal = function(config) {
  return createRequireAuth(config);
};

authMiddlewareUniversal.normalizeEmail = normalizeEmail;
authMiddlewareUniversal.validateEmail = validateEmail;
authMiddlewareUniversal.authMiddleware = authMiddlewareUniversal;
authMiddlewareUniversal.validatePassword = validatePassword;
authMiddlewareUniversal.hashPassword = hashPassword;
authMiddlewareUniversal.verifyPassword = verifyPassword;
authMiddlewareUniversal.createToken = createToken;
authMiddlewareUniversal.requireAuth = requireAuth;

module.exports = authMiddlewareUniversal;
