import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    burst_traffic: {
      executor: 'constant-arrival-rate',
      rate: 120,
      timeUnit: '1s',
      duration: '15s',
      preAllocatedVUs: 20,
      maxVUs: 60,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<50'],
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/v1/demo/resource', {
    headers: { 'X-Client-Id': `vu-${__VU % 5}` },
  });

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'has X-RateLimit-Limit header': (r) => r.headers['X-Ratelimit-Limit'] !== undefined || r.headers['X-RateLimit-Limit'] !== undefined,
  });
  sleep(0.05);
}
