const express = require('express');

const EVENT_COLUMN_CANDIDATES = {
  id: ['event_id', 'id'],
  title: ['event_name', 'name', 'title', '活動名稱'],
  artist: ['artists_summary', 'artist', 'artist_name', 'performer', '藝人'],
  venue: ['venue_name', 'venue', 'location', '地點', '活動地點'],
  address: ['venue_address', 'address', '地址', '活動地址'],
  saleTime: ['ticket_sale_time', 'sale_at', 'saleAt', 'sale_time', '售票時間', '搶票時間'],
  activityTime: ['event_time', 'activity_time', 'activityTime', 'event_date', 'start_time', '活動時間'],
  priceText: ['tickets_summary', 'price_text', 'price', 'priceText', '票價'],
  ticketType: ['ticket_type', 'ticketType', 'tickets_summary', '票種'],
  url: ['event_url', 'url', 'buy_url', 'ticket_url', 'official_url', '購票連結', '網址'],
  source: ['source_site', 'source_website', 'source', 'platform', '平台', '售票平台', '來源網站'],
  createdAt: ['scraped_at', 'created_at', 'createdAt', 'updated_at', '爬取時間']
};

function createRecommendationsRouter({ pool, requireAuth } = {}) {
  const router = express.Router();
  const middlewares = typeof requireAuth === 'function' ? [requireAuth] : [];

  router.get('/', async (req, res) => {
    try {
      const source = await getRecommendationSource(pool);
      const picked = source.picked;
      const limit = Math.max(Math.min(Number(req.query.limit || 80), 200), 1);
      const offset = Math.max(Number(req.query.offset || 0), 0);
      const { whereSql, params } = buildRecommendationWhere(req.query, picked);
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
        FROM ${source.tableSql}
        ${whereSql}
        ORDER BY ${picked.activityTime || picked.createdAt || picked.id || '1'} DESC
        LIMIT ${Number.parseInt(limit, 10)} OFFSET ${Number.parseInt(offset, 10)}
      `;
      const [rows] = await pool.execute(sql, params);
      const [countRows] = await pool.execute(`SELECT COUNT(*) AS total FROM ${source.tableSql} ${whereSql}`, params);
      res.json({
        total: Number(countRows[0]?.total || 0),
        sourceTable: source.tableSql,
        items: rows.map(row => ({
          id: Number(row.id || 0),
          title: normalizeTitle(row),
          artist: String(row.artist || ''),
          venue: String(row.venue || ''),
          address: String(row.address || ''),
          saleTime: String(row.saleTime || ''),
          activityTime: String(row.activityTime || ''),
          price: String(row.priceText || ''),
          ticketType: String(row.ticketType || ''),
          url: String(row.url || ''),
          source: String(row.source || '')
        }))
      });
    } catch (err) {
      console.error('GET /api/recommendations error:', err);
      res.status(500).json({ error: 'server_error', message: err.message });
    }
  });

  router.post('/budget', async (req, res) => {
    try {
      const maxBudget = Number(req.body?.maxBudget || 0);
      const keyword = String(req.body?.keyword || '').trim();
      const location = String(req.body?.location || '').trim();
      const platform = String(req.body?.platform || '').trim();
      const priceBand = String(req.body?.priceBand || '').trim();
      const onlyAffordable = req.body?.onlyAffordable !== false;
      const savedEventIds = Array.isArray(req.body?.savedEventIds) ? req.body.savedEventIds.map(String) : [];
      const recordedVenues = Array.isArray(req.body?.recordedVenues) ? req.body.recordedVenues.map(String).filter(Boolean) : [];
      const strategy = String(req.body?.strategy || 'balanced');
      const limit = Math.max(Math.min(Number(req.body?.limit || 20), 50), 1);
      const periodMonths = normalizePeriodMonths(req.body?.periodMonths);
      const budgetPeriod = normalizeBudgetPeriod(req.body?.budgetPeriod, periodMonths);
      const periodLabel = buildPeriodLabel(periodMonths);

      if (!Number.isFinite(maxBudget) || maxBudget <= 0) {
        return res.status(400).json({ error: 'invalid_budget' });
      }

      const queryLimit = Math.max(Math.min(Number(limit * 20 || 100), 300), 100);
      const selectParams = [];
      const conditions = [];
      const source = await getRecommendationSource(pool);
      const picked = source.picked;

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
        FROM ${source.tableSql}
        ${whereSql}
        ORDER BY ${picked.createdAt || picked.id || '1'} DESC
        LIMIT ${queryLimit}
      `;

      const [rows] = await pool.execute(sql, selectParams);

      const allCandidates = rows
        .map(row => buildRecommendation(row, { maxBudget, keyword, location, platform, savedEventIds, recordedVenues, strategy, budgetPeriod, periodMonths, periodLabel }));
      const candidates = allCandidates
        .filter(item => !platform || item.source.includes(platform))
        .sort((a, b) => sortRecommendations(a, b, { strategy, maxBudget }));

      const items = dedupeRecommendations(candidates)
        .slice(0, limit)
        .map(item => ({ ...item, starRating: scoreToStarRating(item.score) }))
        .map(cleanRecommendationForClient);

      const plans = buildBudgetPlans(items, maxBudget, { budgetPeriod, periodMonths, periodLabel }).map(cleanPlanForClient);
      const suggestions = items.length ? [] : buildEmptySuggestions({ maxBudget, keyword, location, strategy, periodLabel });
      const availableFilters = buildAvailableFilters(allCandidates);
      const insights = buildInsights({ items, plans, maxBudget, periodLabel, keyword, location, platform, savedEventIds, recordedVenues });
      
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
        budgetPeriod,
        periodMonths,
        periodLabel,
        sourceTable: source.tableSql,
        keyword,
        location,
        platform,
        priceBand,
        onlyAffordable,
        personalization: {
          savedEventCount: savedEventIds.length,
          recordedVenueCount: recordedVenues.length
        },
        strategy,
        preference,
        total: items.length,
        candidateTotal: candidates.length,
        availableFilters,
        insights,
        suggestions,
        plans,
        items
      });
    } catch (err) {
      console.error('POST /api/recommendations/budget error:', err);
      res.status(500).json({ error: 'server_error', message: err.message });
    }
  });

  router.get('/budget/user-data', ...middlewares, async (req, res) => {
    try {
      const userId = req.user?.id;
      if (!userId) return res.status(401).json({ error: 'unauthorized' });

      const [reminderRows] = await pool.execute(
        `
        SELECT
          r.id,
          r.event_id AS eventId,
          COALESCE(e.event_name, '') AS title,
          COALESCE(e.venue_name, '') AS venue,
          COALESCE(e.source_site, '') AS platform,
          COALESCE(e.tickets_summary, '') AS priceText,
          COALESCE(DATE_FORMAT(r.reminded_at, '%Y-%m-%d %H:%i:%s'), '') AS remindedAt
        FROM reminders r
        LEFT JOIN v_all_events_summary e ON r.event_id = e.event_id
        WHERE r.user_id = ?
        ORDER BY r.reminded_at ASC, r.id DESC
        `,
        [userId]
      );

      let ticketRows = [];
      try {
        const [rows] = await pool.execute(
          `
          SELECT
            et.id,
            et.event_id AS eventId,
            COALESCE(e.event_name, CONCAT('Event #', et.event_id)) AS title,
            COALESCE(DATE_FORMAT(e.event_time, '%Y-%m-%d %H:%i:%s'), '') AS date,
            COALESCE(e.source_site, '') AS platform,
            CAST(et.price AS UNSIGNED) AS price,
            1 AS quantity,
            0 AS fee,
            COALESCE(et.ticket_type, '') AS note
          FROM event_tickets et
          LEFT JOIN v_all_events_summary e ON et.event_id = e.event_id
          ORDER BY et.id DESC
          LIMIT 80
          `
        );
        ticketRows = rows;
      } catch (ticketErr) {
        console.warn('GET /api/recommendations/budget/user-data event_tickets skipped:', ticketErr.message);
      }

      res.json({
        savedEventIds: reminderRows.map(row => String(row.eventId || '')).filter(Boolean),
        reminders: reminderRows,
        ticketRecords: ticketRows.map(row => ({
          id: String(row.id || `${row.eventId}-${row.price}`),
          title: row.title,
          date: row.date || row.remindedAt || '',
          platform: row.platform || '',
          price: Number(row.price || extractPrices(row.priceText)[0] || 0),
          quantity: Number(row.quantity || 1),
          fee: Number(row.fee || 0),
          note: row.note || ''
        }))
      });
    } catch (err) {
      console.error('GET /api/recommendations/budget/user-data error:', err);
      res.status(500).json({ error: 'server_error', message: err.message });
    }
  });

  return router;
}

function cleanRecommendationForClient(item) { return { ...item, reason: cleanVisibleText(item.reason), riskMessage: cleanVisibleText(item.riskMessage), scoreReasons: item.scoreReasons.map(cleanVisibleText) }; }
function cleanPlanForClient(plan) { return { ...plan, description: cleanVisibleText(plan.description), suitableFor: cleanVisibleText(plan.suitableFor), itemSummaries: plan.itemSummaries.map(cleanVisibleText) }; }
function cleanVisibleText(value) { return String(value || '').replace(/交通、餐飲或備用預算/g, '其他支出').replace(/交通與餐飲費/g, '其他費用').replace(/交通或餐飲預算/g, '其他費用').replace(/交通/g, '其他支出').replace(/餐飲/g, '其他支出').replace(/備用/g, '彈性'); }
function normalizePeriodMonths(value) {
  const months = Math.round(Number(value || 1));
  if (!Number.isFinite(months)) return 1;
  return Math.max(1, Math.min(months, 12));
}
function normalizeBudgetPeriod(value, periodMonths = 1) {
  const text = String(value || '').toLowerCase();
  if (text === 'quarter' || periodMonths === 3) return 'quarter';
  if (periodMonths === 1) return 'monthly';
  return 'custom';
}
function buildPeriodLabel(periodMonths = 1) {
  const months = normalizePeriodMonths(periodMonths);
  if (months === 1) return '1 個月內';
  if (months === 3) return '3 個月內';
  if (months === 6) return '半年內';
  if (months === 12) return '一年內';
  return `${months} 個月內`;
}
async function getRecommendationSource(pool) {
  const databaseName = process.env.DB_NAME || 'defaultdb';
  const hasSummaryView = await tableExists(pool, databaseName, 'v_all_events_summary');
  if (hasSummaryView) {
    const columns = await getColumns(pool, 'v_all_events_summary', databaseName);
    return {
      tableSql: `${quoteIdent(databaseName)}.${quoteIdent('v_all_events_summary')}`,
      picked: {
        id: pickColumn(columns, ['event_id', 'id']),
        title: pickColumn(columns, ['event_name', 'name', 'title']),
        artist: pickColumn(columns, ['artists_summary', 'artist', 'artist_name']),
        venue: pickColumn(columns, ['venue_name', 'venue', 'location']),
        address: pickColumn(columns, ['venue_address', 'address']),
        saleTime: pickColumn(columns, ['ticket_sale_time', 'sale_time', 'sale_at']),
        activityTime: pickColumn(columns, ['event_time', 'activity_time', 'start_time']),
        priceText: pickColumn(columns, ['tickets_summary', 'price_text', 'price']),
        ticketType: pickColumn(columns, ['ticket_type', 'tickets_summary']),
        url: pickColumn(columns, ['event_url', 'url', 'ticket_url', 'official_url']),
        source: pickColumn(columns, ['source_site', 'source_website', 'source', 'platform']),
        createdAt: pickColumn(columns, ['scraped_at', 'created_at', 'updated_at'])
      }
    };
  }

  const hasEvents = await tableExists(pool, databaseName, 'events');
  if (hasEvents) {
    const columns = await getColumns(pool, 'events', databaseName);
    const hasVenues = await tableExists(pool, databaseName, 'venues');
    const venueColumns = hasVenues ? await getColumns(pool, 'venues', databaseName) : new Set();
    const eventTable = `${quoteIdent(databaseName)}.${quoteIdent('events')} e`;
    const venueJoin = hasVenues ? ` LEFT JOIN ${quoteIdent(databaseName)}.${quoteIdent('venues')} v ON e.${quoteIdent('venue_id')} = v.${quoteIdent('id')}` : '';
    return {
      tableSql: `${eventTable}${venueJoin}`,
      picked: {
        id: pickColumnFrom(columns, ['id', 'event_id'], 'e'),
        title: pickColumnFrom(columns, ['name', 'event_name', 'title'], 'e'),
        artist: pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.artist, 'e'),
        venue: pickColumnFrom(venueColumns, ['name', 'venue_name', 'venue'], 'v') || pickColumnFrom(columns, ['venue_name', 'venue', 'venue_id'], 'e'),
        address: pickColumnFrom(venueColumns, ['address', 'venue_address'], 'v') || pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.address, 'e'),
        saleTime: pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.saleTime, 'e'),
        activityTime: pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.activityTime, 'e'),
        priceText: pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.priceText, 'e'),
        ticketType: pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.ticketType, 'e'),
        url: pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.url, 'e'),
        source: pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.source, 'e'),
        createdAt: pickColumnFrom(columns, EVENT_COLUMN_CANDIDATES.createdAt, 'e')
      }
    };
  }

  throw new Error(`Cannot find ${databaseName}.events or ${databaseName}.v_all_events_summary`);
}
function buildRecommendationWhere(query, picked) {
  const conditions = [];
  const params = [];
  const keyword = String(query.keyword || query.q || '').trim();
  const source = String(query.source || query.platform || '').trim();
  const startDate = String(query.startDate || '').trim();
  const endDate = String(query.endDate || '').trim();
  const minPrice = Number(query.minPrice || 0);
  const maxPrice = Number(query.maxPrice || 0);

  if (keyword) {
    const columns = [picked.title, picked.artist, picked.venue, picked.address].filter(Boolean);
    if (columns.length) {
      conditions.push(`(${columns.map(column => `${column} LIKE ?`).join(' OR ')})`);
      columns.forEach(() => params.push(`%${keyword}%`));
    }
  }
  if (source && picked.source) {
    conditions.push(`${picked.source} = ?`);
    params.push(source);
  }
  if (startDate && picked.activityTime) {
    conditions.push(`${picked.activityTime} >= ?`);
    params.push(startDate);
  }
  if (endDate && picked.activityTime) {
    conditions.push(`${picked.activityTime} <= ?`);
    params.push(endDate);
  }
  if ((minPrice > 0 || maxPrice > 0) && picked.priceText) {
    const numericPrice = `CAST(NULLIF(REGEXP_SUBSTR(REPLACE(${picked.priceText}, ',', ''), '[0-9]+'), '') AS UNSIGNED)`;
    if (minPrice > 0) {
      conditions.push(`(${numericPrice} IS NULL OR ${numericPrice} >= ?)`);
      params.push(minPrice);
    }
    if (maxPrice > 0) {
      conditions.push(`(${numericPrice} IS NULL OR ${numericPrice} <= ?)`);
      params.push(maxPrice);
    }
  }
  return {
    whereSql: conditions.length ? `WHERE ${conditions.join(' AND ')}` : '',
    params
  };
}
async function tableExists(pool, schemaName, tableName) {
  const [rows] = await pool.query(
    'SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? LIMIT 1',
    [schemaName, tableName]
  );
  return rows.length > 0;
}
async function getColumns(pool, tableName, schemaName = process.env.DB_NAME || 'defaultdb') {
  const [rows] = await pool.query(`SHOW COLUMNS FROM ${quoteIdent(schemaName)}.${quoteIdent(tableName)}`);
  return new Set(rows.map(row => row.Field));
}
function quoteIdent(value) { return `\`${String(value).replace(/`/g, '``')}\``; }
function pickColumn(columns, names) { const name = names.find(candidate => columns.has(candidate)); return name ? `\`${name}\`` : ''; }
function pickColumnFrom(columns, names, alias) { const name = names.find(candidate => columns.has(candidate)); return name ? `${alias}.${quoteIdent(name)}` : ''; }
function selectExpression(column, alias, fallback = "''") { return column ? `COALESCE(${column}, ${fallback}) AS ${alias}` : `${fallback} AS ${alias}`; }

function buildRecommendation(row, filters) {
  const prices = extractPrices(row.priceText);
  const minPrice = prices.length ? Math.min(...prices) : 0;
  const maxPrice = prices.length ? Math.max(...prices) : 0;
  const effectivePrice = minPrice || maxPrice || 0;
  const budgetLeft = effectivePrice > 0 ? Math.max(filters.maxBudget - effectivePrice, 0) : 0;
  const budgetUsageRate = effectivePrice > 0 ? effectivePrice / filters.maxBudget : 0;
  const title = normalizeTitle(row);

  let score = 18;
  const reasonParts = [];

  if (!prices.length) {
    score += 4;
    reasonParts.push('票價未標示，購買前建議再確認售票頁');
  } else {
    const budgetFit = scoreBudgetFit(budgetUsageRate, filters.strategy);
    score += budgetFit.points;
    if (budgetFit.reason) reasonParts.push(budgetFit.reason);
    reasonParts.push(`最低票價約 NT$ ${minPrice}`);
    if (maxPrice > minPrice) reasonParts.push(`最高票價約 NT$ ${maxPrice}`);
  }

  if (filters.keyword && [row.title, row.artist, row.source].join(' ').includes(filters.keyword)) {
    score += 12;
    reasonParts.push('符合搜尋偏好');
  }
  if (filters.location && `${row.venue} ${row.address}`.includes(filters.location)) {
    score += 10;
    reasonParts.push('符合地點偏好');
  }
  if (filters.platform && String(row.source || '').includes(filters.platform)) {
    score += 6;
    reasonParts.push('符合平台偏好');
  }
  if (filters.savedEventIds?.includes(String(row.id || ''))) {
    score += 8;
    reasonParts.push('已在你的收藏或關注清單中');
  }
  if (filters.recordedVenues?.some(value => value && `${row.venue} ${row.source}`.includes(value))) {
    score += 5;
    reasonParts.push('符合過去使用紀錄偏好');
  }
  if (budgetUsageRate > 0) {
    if (budgetUsageRate <= 0.35) score += 8;
    else if (budgetUsageRate <= 0.75) score += 4;
    else score += 1;
  }

  const finalScore = Math.max(Math.min(Math.round(score), 100), 0);
  const riskLevel = buildRiskLevel({ prices, budgetUsageRate, maxPrice, maxBudget: filters.maxBudget });
  const reminder = buildReminder(row.saleTime);

  return {
    id: String(row.id || ''), title, artist: String(row.artist || ''), venue: String(row.venue || ''),
    saleTime: String(row.saleTime || ''), activityTime: String(row.activityTime || ''),
    priceText: String(row.priceText || ''), ticketType: String(row.ticketType || ''),
    minPrice, maxPrice, estimatedSpend: effectivePrice, budgetLeft,
    budgetUsageRate: Math.round(budgetUsageRate * 100), budgetUsageLabel: budgetUsageLabel(budgetUsageRate),
    score: finalScore, valueLevel: scoreToValueLevel(finalScore), starRating: scoreToStarRating(finalScore),
    reason: buildHumanReason({ ...row, title }, { ...filters, effectivePrice, budgetLeft, riskLevel, reasonParts }),
    riskLevel: riskLevel.level, riskMessage: riskLevel.message, url: String(row.url || ''), source: String(row.source || ''),
    scoreReasons: buildScoreReasons({ prices, budgetUsageRate, row, filters, riskLevel, reasonParts }),
    reminderStatus: reminder.status,
    reminderLabel: reminder.label,
    priceSource: String(row.priceText || '')
  };
}

function scoreBudgetFit(rate, strategy) {
  if (!rate) return { points: 0, reason: '' };
  const config = strategy === 'saving'
    ? { target: 0.12, spread: 0.12, maxPoints: 22, reasonHit: '價格很輕量，適合保留預算彈性', reasonMid: '價格輕巧，符合省預算目標', reasonLow: '價格偏離省預算區間' }
    : strategy === 'value'
      ? { target: 0.2, spread: 0.14, maxPoints: 28, reasonHit: '價格落在高價值區間', reasonMid: '價格接近高價值區間', reasonLow: '價格偏離高價值區間' }
      : { target: 0.18, spread: 0.16, maxPoints: 26, reasonHit: '價格落在理想區間', reasonMid: '價格接近推薦區間', reasonLow: '價格偏離推薦區間' };
  const distance = Math.abs(rate - config.target);
  const normalized = Math.max(0, 1 - distance / config.spread);
  const points = Math.round(normalized * config.maxPoints);
  const reason = points >= config.maxPoints * 0.75 ? config.reasonHit : points >= config.maxPoints * 0.35 ? config.reasonMid : config.reasonLow;
  return { points, reason };
}

function scoreToValueLevel(score) { if (score >= 50) return 'excellent'; if (score >= 46) return 'good'; if (score >= 41) return 'fair'; return 'low'; }
function scoreToStarRating(score) {
  const normalized = Math.max(0, Math.min(100, Number(score || 0)));
  if (normalized >= 50) return 5;
  if (normalized >= 46) return 4;
  if (normalized >= 41) return 3;
  if (normalized >= 36) return 2;
  return 1;
}
function budgetUsageLabel(rate) { if (!rate) return '票價未明'; if (rate < 0.35) return '低使用'; if (rate <= 0.75) return '合理使用'; return '高風險'; }

function buildHumanReason(row, context) { return `${String(row.title || '這場活動')}最低可見票價約 NT$ ${context.effectivePrice}，${context.effectivePrice > 0 ? `低於你的${context.periodLabel} NT$ ${context.maxBudget} 預算，預估還有 NT$ ${context.budgetLeft} 餘裕` : `需要再確認是否符合${context.periodLabel} NT$ ${context.maxBudget} 預算`}；${row.venue ? `地點在${row.venue}` : '地點未標示'}。${context.reasonParts.slice(0, 3).join('、') || `依據${context.periodLabel}預算條件排序推薦`}。`; }
function normalizeTitle(row) { const title = String(row.title || '').trim(); if (title && title !== 'Untitled Event' && title !== '未取得') return title; const artist = String(row.artist || '').trim(); if (artist && artist !== '未取得') return artist; return String(row.source || '').trim() ? `${row.source}活動` : 'Untitled Event'; }
function sortRecommendations(a, b, { strategy, maxBudget }) { const scoreDiff = b.score - a.score; if (Math.abs(scoreDiff) >= 3) return scoreDiff; if (strategy === 'saving') return a.estimatedSpend - b.estimatedSpend || scoreDiff; return Math.abs(a.estimatedSpend / maxBudget - (strategy === 'value' ? 0.2 : 0.18)) - Math.abs(b.estimatedSpend / maxBudget - (strategy === 'value' ? 0.2 : 0.18)) || scoreDiff; }
function dedupeRecommendations(items) { const picked = new Map(); for (const item of items) { const key = `${String(item.title).toLowerCase().slice(0,16)}|${String(item.venue).toLowerCase().slice(0,8)}`; if (!picked.has(key) || item.score > picked.get(key).score) picked.set(key, item); } return [...picked.values()]; }
function budgetUsageLabel(rate) { if (!rate) return '票價未明'; if (rate < 0.35) return '低使用'; if (rate <= 0.75) return '合理使用'; return '高風險'; }
function buildRiskLevel({ prices, budgetUsageRate, maxPrice, maxBudget }) { if (!prices.length) return { level: 'unknown', message: '票價未明，購買前建議先確認售票頁面' }; if (maxPrice > maxBudget) return { level: 'medium', message: '部分票種可能超出預算，建議優先選擇低價區' }; if (budgetUsageRate >= 0.9) return { level: 'high', message: '票價接近預算上限，建議預留額外交通與餐飲費' }; return { level: 'low', message: '預算風險較低，可以列為優先考慮' }; }

function buildScoreReasons({ prices, budgetUsageRate, row, filters, riskLevel, reasonParts }) {
  const reasons = [];
  if (prices.length) reasons.push(`最低票價約 NT$ ${Math.min(...prices)}`);
  if (budgetUsageRate > 0 && budgetUsageRate <= 0.35) reasons.push('預算壓力低');
  else if (budgetUsageRate > 0 && budgetUsageRate <= 0.75) reasons.push('預算使用合理');
  else if (budgetUsageRate > 0.75) reasons.push('接近預算上限');
  if (filters.keyword && [row.title, row.artist, row.source].join(' ').includes(filters.keyword)) reasons.push('符合搜尋偏好');
  if (filters.location && `${row.venue} ${row.address}`.includes(filters.location)) reasons.push('符合地點偏好');
  if (filters.savedEventIds?.includes(String(row.id || ''))) reasons.push('符合收藏偏好');
  if (filters.recordedVenues?.some(value => value && `${row.venue} ${row.source}`.includes(value))) reasons.push('符合過去紀錄');
  if (riskLevel.level === 'low') reasons.push('購買風險低');
  return [...new Set([...reasons, ...reasonParts])].slice(0, 4);
}

function buildReminder(saleTime) {
  const text = String(saleTime || '').trim();
  if (!text) return { status: 'unknown', label: '售票時間待確認' };
  const time = Date.parse(text);
  if (!Number.isFinite(time)) return { status: 'announced', label: `售票資訊：${text}` };
  const diffDays = Math.ceil((time - Date.now()) / 86400000);
  if (diffDays < 0) return { status: 'on_sale', label: '已開賣，建議確認剩餘票種' };
  if (diffDays <= 7) return { status: 'soon', label: `售票倒數 ${diffDays} 天，建議加入提醒` };
  return { status: 'upcoming', label: `售票時間 ${formatDate(time)}，可先收藏追蹤` };
}

function formatDate(time) {
  return new Date(time).toISOString().slice(0, 10);
}

function matchesPriceBand(item, priceBand, maxBudget) {
  if (!priceBand) return true;
  const spend = Number(item.estimatedSpend || 0);
  if (!spend) return priceBand === 'unknown';
  if (priceBand === 'low') return spend <= Math.max(maxBudget * 0.25, 800);
  if (priceBand === 'mid') return spend > Math.max(maxBudget * 0.25, 800) && spend <= Math.max(maxBudget * 0.6, 2500);
  if (priceBand === 'high') return spend > Math.max(maxBudget * 0.6, 2500);
  return true;
}

function buildAvailableFilters(items) {
  const venues = [...new Set(items.map(item => item.venue).filter(Boolean))].slice(0, 12);
  const platforms = [...new Set(items.map(item => item.source).filter(Boolean))].slice(0, 12);
  const cities = [...new Set(items.map(item => inferCity(`${item.venue} ${item.reason}`)).filter(Boolean))].slice(0, 8);
  return { cities, venues, platforms };
}

function inferCity(text) {
  const source = String(text || '');
  return ['台北', '新北', '桃園', '台中', '台南', '高雄', '基隆', '新竹'].find(city => source.includes(city)) || '';
}

function buildInsights({ items, plans, maxBudget, periodLabel, keyword, location, platform, savedEventIds = [], recordedVenues = [] }) {
  const priced = items.filter(item => item.estimatedSpend > 0);
  const avg = priced.length ? Math.round(priced.reduce((sum, item) => sum + item.estimatedSpend, 0) / priced.length) : 0;
  const lowRisk = items.filter(item => item.riskLevel === 'low').length;
  const insights = [];
  if (priced.length) insights.push(`${periodLabel}找到 ${priced.length} 場有明確票價的活動，平均最低票價約 NT$ ${avg}`);
  if (plans.length) insights.push(`已整理 ${plans.length} 種預算方案，可直接比較單場、組合與低壓力選擇`);
  if (lowRisk) insights.push(`${lowRisk} 場活動屬於低預算風險`);
  if (keyword || location || platform) insights.push('目前排序已套用你的搜尋與偏好條件');
  if (savedEventIds.length || recordedVenues.length) insights.push('已納入收藏與使用紀錄作為個人化排序依據');
  if (maxBudget < 3000) insights.push('目前預算偏低，建議優先查看低壓力方案');
  return insights.slice(0, 4);
}

function buildBudgetPlans(items, maxBudget, period = {}) {
  const priced = items
    .filter(item => item.estimatedSpend > 0 && item.estimatedSpend <= maxBudget)
    .sort((a, b) => b.score - a.score || a.estimatedSpend - b.estimatedSpend);
  if (!priced.length) return [];
  const periodLabel = period.periodLabel || '本期';
  const plans = [];
  const premium = [...priced].sort((a, b) => {
    const scoreDiff = b.score - a.score;
    if (Math.abs(scoreDiff) >= 3) return scoreDiff;
    return b.estimatedSpend - a.estimatedSpend || scoreDiff;
  })[0];
  const combo = buildComboPlanItems(priced, maxBudget);
  const saving = [...priced].sort((a, b) => a.estimatedSpend - b.estimatedSpend || b.score - a.score).find(item => item.id !== premium?.id) || priced[0];

  if (premium) {
    plans.push({
      type: 'premium',
      title: '精選單場',
      description: `以${periodLabel}預算優先選擇「${premium.title}」，推薦星等 ${premium.starRating || scoreToStarRating(premium.score)} / 5，預估支出 NT$ ${premium.estimatedSpend}。`,
      actionLabel: '查看精選',
      suitableFor: `想用${periodLabel}預算先鎖定一場高把握活動`,
      estimatedSpend: premium.estimatedSpend,
      budgetLeft: Math.max(maxBudget - premium.estimatedSpend, 0),
      eventIds: [premium.id],
      decisionLabel: premium.score >= 50 ? '推薦購買' : premium.score >= 41 ? '可以考慮' : '建議觀望',
      itemSummaries: [`${premium.title}：${premium.starRating || scoreToStarRating(premium.score)} 顆星，預估 NT$ ${premium.estimatedSpend}`]
    });
  }
  if (combo.length >= 2) {
    const total = combo.reduce((sum, item) => sum + item.estimatedSpend, 0);
    plans.push({
      type: 'combo',
      title: '多場組合',
      description: `安排 ${combo.length} 場活動，預估總支出 NT$ ${total}，在${periodLabel}預算內保留 NT$ ${Math.max(maxBudget - total, 0)}。`,
      actionLabel: '查看組合',
      suitableFor: `想在${periodLabel}多參加幾場活動`,
      estimatedSpend: total,
      budgetLeft: Math.max(maxBudget - total, 0),
      eventIds: combo.map(item => item.id),
      decisionLabel: total <= maxBudget * 0.75 ? '可以安排' : '接近預算上限',
      itemSummaries: combo.map(item => `${item.title}：NT$ ${item.estimatedSpend}`)
    });
  }
  if (saving) {
    plans.push({
      type: 'saving',
      title: '低壓力選擇',
      description: `選擇「${saving.title}」可把支出控制在 NT$ ${saving.estimatedSpend}，保留最多後續調整空間。`,
      actionLabel: '查看低壓力方案',
      suitableFor: `想降低${periodLabel}支出壓力`,
      estimatedSpend: saving.estimatedSpend,
      budgetLeft: Math.max(maxBudget - saving.estimatedSpend, 0),
      eventIds: [saving.id],
      decisionLabel: '低壓力',
      itemSummaries: [`${saving.title}：保留 NT$ ${Math.max(maxBudget - saving.estimatedSpend, 0)}`]
    });
  }
  return plans;
}

function buildComboPlanItems(items, maxBudget) {
  const selected = [];
  let total = 0;
  const pool = [...items].sort((a, b) => {
    const aValue = a.score / Math.max(a.estimatedSpend, 1);
    const bValue = b.score / Math.max(b.estimatedSpend, 1);
    return bValue - aValue || b.score - a.score;
  });
  for (const item of pool) {
    if (selected.some(picked => picked.id === item.id)) continue;
    if (selected.length >= 4) break;
    if (total + item.estimatedSpend <= maxBudget) {
      selected.push(item);
      total += item.estimatedSpend;
    }
  }
  return selected;
}

function buildEmptySuggestions({ maxBudget, periodLabel = '本期' }) { return [{ type: 'increase_budget', title: `調整${periodLabel}預算上限`, description: `可先嘗試調高${periodLabel} NT$ ${maxBudget} 預算，或放寬關鍵字與地點條件。` }]; }
function extractPrices(priceText) { const text = String(priceText || ''); if (!text.trim()) return []; if (/免費|免票|索票/i.test(text)) return [0]; const values = Array.from(text.replace(/,/g, '').matchAll(/\d+/g)).map(m => Number(m[0])).filter(v => v >= 100 && v <= 50000); return [...new Set(values)]; }

module.exports = createRecommendationsRouter;

