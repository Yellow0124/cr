const express = require('express');

const EVENT_COLUMN_CANDIDATES = {
  id: ['event_id', 'id'],
  title: ['title', 'name', 'event_name', '活動名稱'],
  artist: ['artist', 'performer', '藝人'],
  venue: ['venue', 'location', '地點', '活動地點'],
  address: ['address', '地址', '活動地址'],
  saleTime: ['sale_at', 'saleAt', 'sale_time', '售票時間', '搶票時間'],
  activityTime: ['activity_time', 'activityTime', '活動時間'],
  priceText: ['price', 'priceText', '票價'],
  ticketType: ['ticket_type', 'ticketType', '票種'],
  url: ['url', 'buy_url', '購票連結', '網址'],
  source: ['source', 'platform', '平台', '售票平台', '來源網站'],
  createdAt: ['created_at', 'createdAt', '爬取時間']
};

function createRecommendationsRouter({ pool, requireAuth } = {}) {
  const router = express.Router();
  const middlewares = typeof requireAuth === 'function' ? [requireAuth] : [];

  router.post('/budget', ...middlewares, async (req, res) => {
    try {
      const maxBudget = Number(req.body?.maxBudget || 0);
      const keyword = String(req.body?.keyword || '').trim();
      const location = String(req.body?.location || '').trim();
      const strategy = String(req.body?.strategy || 'balanced');
      const limit = Math.max(Math.min(Number(req.body?.limit || 20), 50), 1);

      if (!Number.isFinite(maxBudget) || maxBudget <= 0) {
        return res.status(400).json({ error: 'invalid_budget' });
      }

      const columns = await getEventColumns(pool);
      const picked = Object.fromEntries(
        Object.entries(EVENT_COLUMN_CANDIDATES).map(([key, names]) => [key, pickColumn(columns, names)])
      );

      const queryLimit = Math.max(Math.min(Number(limit * 20 || 100), 300), 100);
      const selectParams = [];
      const conditions = [];

      if (keyword) {
        const keywordColumns = [picked.title, picked.artist, picked.source].filter(Boolean);
        if (keywordColumns.length) {
          conditions.push(`(${keywordColumns.map(column => `${column} LIKE ?`).join(' OR ')})`);
          keywordColumns.forEach(() => selectParams.push(`%${keyword}%`));
        }
      }

      if (location) {
        const locationColumns = [picked.venue, picked.address].filter(Boolean);
        if (locationColumns.length) {
          conditions.push(`(${locationColumns.map(column => `${column} LIKE ?`).join(' OR ')})`);
          locationColumns.forEach(() => selectParams.push(`%${location}%`));
        }
      }

      const whereSql = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
      const sql = `
        SELECT
          ${selectExpression(picked.id, 'id', "'0'")},
          ${selectExpression(picked.title, 'title', "'Untitled Event'")},
          ${selectExpression(picked.artist, 'artist')},
          ${selectExpression(picked.venue, 'venue')},
          ${selectExpression(picked.address, 'address')},
          ${selectExpression(picked.saleTime, 'saleTime')},
          ${selectExpression(picked.activityTime, 'activityTime')},
          ${selectExpression(picked.priceText, 'priceText')},
          ${selectExpression(picked.ticketType, 'ticketType')},
          ${selectExpression(picked.url, 'url')},
          ${selectExpression(picked.source, 'source')},
          ${selectExpression(picked.createdAt, 'createdAt')}
        FROM events
        ${whereSql}
        ORDER BY ${picked.createdAt || picked.id || '1'} DESC
        LIMIT ${queryLimit}
      `;

      const [rows] = await pool.execute(sql, selectParams);

      const candidates = rows
        .map(row => buildRecommendation(row, { maxBudget, keyword, location, strategy }))
        .filter(item => item.maxPrice === 0 || item.minPrice <= maxBudget)
        .sort((a, b) => sortRecommendations(a, b, { strategy, maxBudget }));

      const items = dedupeRecommendations(candidates)
        .slice(0, limit)
        .map(cleanRecommendationForClient);

      const plans = buildBudgetPlans(items, maxBudget).map(cleanPlanForClient);
      const suggestions = items.length ? [] : buildEmptySuggestions({ maxBudget, keyword, location, strategy });
      
      // 安全獲取使用者偏好紀錄
      const userId = req.user?.id || null;
      let preference = null;
      if (userId) {
        try {
          const [prefRows] = await pool.execute(
            'SELECT max_budget AS maxBudget, keyword, location, strategy FROM budget_recommendation_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT 1',
            [userId]
          );
          preference = prefRows[0] || null;

          // 儲存本次偏好紀錄
          await pool.execute(
            'INSERT INTO budget_recommendation_logs (user_id, max_budget, keyword, location, strategy, result_count, top_event_id, top_score) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
            [userId, maxBudget, keyword, location, strategy, items.length, items[0]?.id || null, items[0]?.score || 0]
          );
        } catch (logErr) {
          console.warn('⚠️ 偏好日誌紀錄跳過 (可能尚未建立 budget_recommendation_logs 表):', logErr.message);
        }
      }

      res.json({
        maxBudget,
        keyword,
        location,
        strategy,
        preference,
        total: items.length,
        candidateTotal: candidates.length,
        suggestions,
        plans,
        items
      });
    } catch (err) {
      console.error('POST /api/recommendations/budget error:', err);
      res.status(500).json({ error: 'server_error', message: err.message });
    }
  });

  return router;
}

function cleanRecommendationForClient(item) { return { ...item, reason: cleanVisibleText(item.reason), riskMessage: cleanVisibleText(item.riskMessage) }; }
function cleanPlanForClient(plan) { return { ...plan, description: cleanVisibleText(plan.description), suitableFor: cleanVisibleText(plan.suitableFor) }; }
function cleanVisibleText(value) { return String(value || '').replace(/交通、餐飲或備用預算/g, '其他支出').replace(/交通與餐飲費/g, '其他費用').replace(/交通或餐飲預算/g, '其他費用').replace(/交通/g, '其他支出').replace(/餐飲/g, '其他支出').replace(/備用/g, '彈性'); }
async function getEventColumns(pool) { const [rows] = await pool.query('SHOW COLUMNS FROM events'); return new Set(rows.map(row => row.Field)); }
function pickColumn(columns, names) { const name = names.find(candidate => columns.has(candidate)); return name ? `\`${name}\`` : ''; }
define = function selectExpression(column, alias, fallback = "''") { return column ? `COALESCE(${column}, ${fallback}) AS ${alias}` : `${fallback} AS ${alias}`; }

function buildRecommendation(row, filters) {
  const prices = extractPrices(row.priceText);
  const minPrice = prices.length ? Math.min(...prices) : 0;
  const maxPrice = prices.length ? Math.max(...prices) : 0;
  const effectivePrice = minPrice || maxPrice || 0;
  const budgetLeft = effectivePrice > 0 ? Math.max(filters.maxBudget - effectivePrice, 0) : 0;
  const budgetUsageRate = effectivePrice > 0 ? effectivePrice / filters.maxBudget : 0;
  const title = normalizeTitle(row);

  const scoreBreakdown = []; let score = 28; const reasonParts = [];
  if (!prices.length) { score += 2; reasonParts.push('票價未標示，購買前建議再確認售票頁'); } 
  else if (minPrice <= filters.maxBudget && maxPrice <= filters.maxBudget) { score += 30; reasonParts.push(`票價完全落在 NT$ ${filters.maxBudget} 預算內`); } 
  else if (minPrice <= filters.maxBudget) { score += 17; reasonParts.push('最低票價符合預算，但高價區可能超出預算'); }

  const budgetFit = scoreBudgetFit(budgetUsageRate, filters.strategy);
  score += budgetFit.points; if (budgetFit.reason) reasonParts.push(budgetFit.reason);
  if (filters.keyword && [row.title, row.artist, row.source].join(' ').includes(filters.keyword)) score += 16;
  if (filters.location && `${row.venue} ${row.address}`.includes(filters.location)) score += 14;

  const finalScore = Math.max(Math.min(Math.round(score), 100), 0);
  const riskLevel = buildRiskLevel({ prices, budgetUsageRate, maxPrice, maxBudget: filters.maxBudget });

  return {
    id: String(row.id || ''), title, artist: String(row.artist || ''), venue: String(row.venue || ''),
    saleTime: String(row.saleTime || ''), activityTime: String(row.activityTime || ''),
    priceText: String(row.priceText || ''), ticketType: String(row.ticketType || ''),
    minPrice, maxPrice, estimatedSpend: effectivePrice, budgetLeft,
    budgetUsageRate: Math.round(budgetUsageRate * 100), budgetUsageLabel: budgetUsageLabel(budgetUsageRate),
    score: finalScore, valueLevel: scoreToValueLevel(finalScore),
    reason: buildHumanReason({ ...row, title }, { ...filters, effectivePrice, budgetLeft, riskLevel, reasonParts }),
    riskLevel: riskLevel.level, riskMessage: riskLevel.message, url: String(row.url || ''), source: String(row.source || '')
  };
}

function buildHumanReason(row, context) { return `${String(row.title || '這場活動')}最低可見票價約 NT$ ${context.effectivePrice}，${context.effectivePrice > 0 ? `低於你的 NT$ ${context.maxBudget} 預算，預估還有 NT$ ${context.budgetLeft} 餘裕` : `需要再確認是否符合 NT$ ${context.maxBudget} 預算`}；${row.venue ? `地點在${row.venue}` : '地點未標示'}。${context.reasonParts.slice(0, 3).join('、') || '依據目前條件排序推薦'}。`; }
function normalizeTitle(row) { const title = String(row.title || '').trim(); if (title && title !== 'Untitled Event' && title !== '未取得') return title; const artist = String(row.artist || '').trim(); if (artist && artist !== '未取得') return artist; return String(row.source || '').trim() ? `${row.source}活動` : 'Untitled Event'; }
function scoreBudgetFit(rate, strategy) { if (!rate) return { points: 0, reason: '' }; if (strategy === 'saving') { if (rate <= 0.4) return { points: 18, reason: '預算保留度高，符合省預算優先' }; if (rate <= 0.7) return { points: 12, reason: '票價低於預算主要區間' }; return { points: 4, reason: '票價稍高，但仍在預算內' }; } const target = strategy === 'value' ? 0.55 : 0.7; const distance = Math.abs(rate - target); const points = Math.round(Math.max(0, 20 - distance * 45)); return { points, reason: '預算使用比例適中' }; }
function sortRecommendations(a, b, { strategy, maxBudget }) { const scoreDiff = b.score - a.score; if (Math.abs(scoreDiff) >= 3) return scoreDiff; if (strategy === 'saving') return a.estimatedSpend - b.estimatedSpend || scoreDiff; return Math.abs(a.estimatedSpend / maxBudget - (strategy === 'value' ? 0.55 : 0.7)) - Math.abs(b.estimatedSpend / maxBudget - (strategy === 'value' ? 0.55 : 0.7)) || scoreDiff; }
function dedupeRecommendations(items) { const picked = new Map(); for (const item of items) { const key = `${String(item.title).toLowerCase().slice(0,16)}|${String(item.venue).toLowerCase().slice(0,8)}`; if (!picked.has(key) || item.score > picked.get(key).score) picked.set(key, item); } return [...picked.values()]; }
function scoreToValueLevel(score) { if (score >= 85) return 'excellent'; if (score >= 70) return 'good'; if (score >= 55) return 'fair'; return 'low'; }
function budgetUsageLabel(rate) { if (!rate) return '票價未明'; if (rate < 0.35) return '低使用'; if (rate <= 0.75) return '合理使用'; return '高風險'; }
function buildRiskLevel({ prices, budgetUsageRate, maxPrice, maxBudget }) { if (!prices.length) return { level: 'unknown', message: '票價未明，購買前建議先確認售票頁面' }; if (maxPrice > maxBudget) return { level: 'medium', message: '部分票種可能超出預算，建議優先選擇低價區' }; if (budgetUsageRate >= 0.9) return { level: 'high', message: '票價接近預算上限，建議預留額外交通與餐飲費' }; return { level: 'low', message: '預算風險較低，可以列為優先考慮' }; }

function buildBudgetPlans(items, maxBudget) {
  const priced = items.filter(item => item.estimatedSpend > 0); if (!priced.length) return [];
  const premium = [...priced].sort((a, b) => b.score - a.score)[0];
  const plans = [];
  if (premium) {
    plans.push({
      type: 'premium', title: '方案 A：主打一場高適配活動',
      description: `選擇「${premium.title}」，票價約 NT$ ${premium.estimatedSpend}。`,
      actionLabel: '只看這場', suitableFor: '想要簡單決策、優先選一場的使用者',
      estimatedSpend: premium.estimatedSpend, budgetLeft: maxBudget - premium.estimatedSpend, eventIds: [premium.id]
    });
  }
  return plans;
}

function buildEmptySuggestions({ maxBudget }) { return [{ type: 'increase_budget', title: '提高預算上限', description: `可先嘗試調高預算，會有更多票價區間可選。` }]; }
function extractPrices(priceText) { const text = String(priceText || ''); if (!text.trim()) return []; if (/免費|免票|索票/i.test(text)) return [0]; const values = Array.from(text.replace(/,/g, '').matchAll(/\d+/g)).map(m => Number(m[0])).filter(v => v >= 100 && v <= 50000); return [...new Set(values)]; }

module.exports = createRecommendationsRouter;