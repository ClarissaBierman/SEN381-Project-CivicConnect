/*
 * CivicConnect load test - staff filtering the request list (API-07, FR-006, NFR-001, ASR-004).
 *
 * Why this operation: it is the screen staff use all day, it is the most expensive read
 * (filters + sort + paging + handler names), and FR-006 sets a measurable target for it.
 *
 * Targets (from the PED):
 *   FR-006   filter/sort results in 2 s or less        -> p(95) of the list call < 2000 ms
 *   NFR-001  95th percentile <= 3 s at <= 20 users      -> covered by the stricter 2 s threshold
 *   errors   less than 1 % failed calls
 *
 * Workload: 20 concurrent staff users (the pilot load in assumption A-01), each with their own account,
 * changing filters every 2-5 s (think time), for 3 minutes after a 30 s ramp-up.
 *
 * Run:  k6 run -e BASE_URL=http://localhost:8080 perf/request-list.k6.js
 * Needs perf/seed-perf-data.sql loaded, and see perf/README.md about the per-IP rate limit.
 */
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PASSWORD = __ENV.PERF_PASSWORD || 'Password123!';
const USERS = 20;
// MODE=stress: exploratory headroom probe (50 users, no think time, 1 minute). Not the pass/fail test.
const STRESS = __ENV.MODE === 'stress';

const rateLimited = new Counter('rate_limited_429');

export const options = {
  scenarios: STRESS
    ? { stress: { executor: 'constant-vus', vus: 50, duration: '1m' } }
    : {
    staff_filtering: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: USERS },
        { duration: '3m', target: USERS },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration{name:request-list}': ['p(95)<2000'],
    'http_req_failed{name:request-list}': ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// One login per virtual user, before the measured part starts
export function setup() {
  const tokens = [];
  for (let i = 1; i <= USERS; i++) {
    const email = `perf.staff${String(i).padStart(2, '0')}@civicconnect.example`;
    const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password: PASSWORD }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'login' },
    });
    if (res.status !== 200) fail(`login failed for ${email}: ${res.status} ${res.body}`);
    tokens.push(res.json('token'));
  }
  const cats = http.get(`${BASE_URL}/api/v1/categories`, {
    headers: { Authorization: `Bearer ${tokens[0]}` },
    tags: { name: 'categories' },
  });
  return { tokens, categoryIds: cats.json().map((c) => c.id) };
}

const STATUSES = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'];
const SORTS = ['createdAt,desc', 'createdAt,desc', 'createdAt,asc', 'priority,desc'];
const pick = (list) => list[Math.floor(Math.random() * list.length)];

function isoDaysAgo(days) {
  return new Date(Date.now() - days * 86400000).toISOString().slice(0, 10);
}

// The same mix of filters a handler uses on the queue screen
function randomQuery(categoryIds) {
  const params = [`size=20`, `sort=${pick(SORTS)}`, `page=${Math.random() < 0.8 ? 0 : 1 + Math.floor(Math.random() * 3)}`];
  if (Math.random() < 0.6) params.push(`status=${pick(STATUSES)}`);
  if (Math.random() < 0.5) params.push(`categoryId=${pick(categoryIds)}`);
  if (Math.random() < 0.4) params.push(`from=${isoDaysAgo(pick([7, 30, 90]))}`);
  if (Math.random() < 0.2) params.push(`to=${isoDaysAgo(1)}`);
  return params.join('&');
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const res = http.get(`${BASE_URL}/api/v1/requests?${randomQuery(data.categoryIds)}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'request-list' },
  });
  if (res.status === 429) rateLimited.add(1);
  check(res, {
    'status 200': (r) => r.status === 200,
    'page returned': (r) => r.status === 200 && Array.isArray(r.json('content')),
  });
  if (!STRESS) sleep(2 + Math.random() * 3);
}

export function handleSummary(data) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  // setup_data holds the login tokens from setup(); keep them out of the saved results
  const { setup_data, ...results } = data;
  return {
    stdout: textSummary(data),
    [`perf/results/request-list-${STRESS ? 'stress-' : ''}${stamp}.json`]: JSON.stringify(results, null, 2),
  };
}

function textSummary(data) {
  const d = data.metrics['http_req_duration{name:request-list}'];
  const f = data.metrics['http_req_failed{name:request-list}'];
  const reqs = data.metrics['http_reqs'];
  const ms = (v) => `${v.toFixed(1)} ms`;
  const lines = [
    '',
    `CivicConnect request-list load test (${STRESS ? 'stress probe' : 'pilot workload'})`,
    `  list calls:         ${data.metrics['iterations'].values.count}`,
    `  avg / median:       ${ms(d.values.avg)} / ${ms(d.values.med)}`,
    `  p90 / p95 / p99:    ${ms(d.values['p(90)'])} / ${ms(d.values['p(95)'])} / ${ms(d.values['p(99)'])}`,
    `  max:                ${ms(d.values.max)}`,
    `  failed calls:       ${(100 * f.values.rate).toFixed(2)} %`,
    `  429 responses:      ${data.metrics['rate_limited_429'] ? data.metrics['rate_limited_429'].values.count : 0}`,
    `  throughput (all):   ${reqs.values.rate.toFixed(2)} requests/s`,
    `  thresholds:         ${Object.entries(data.metrics)
      .filter(([, m]) => m.thresholds)
      .map(([k, m]) => `${k} ${Object.values(m.thresholds).every((t) => t.ok) ? 'PASS' : 'FAIL'}`)
      .join('; ')}`,
    '',
  ];
  return lines.join('\n');
}
