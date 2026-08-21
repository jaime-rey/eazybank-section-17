import http from 'k6/http';
import { check } from 'k6';

const URL = 'http://localhost:8072/eazybank/accounts/api/fetchCustomerDetails?mobileNumber=9345432123';

export const options = {
  scenarios: {
    // Phase 1: warm-up. Opens connections, lets the JVM and pools get
    // "warm". Its results do NOT count towards the final thresholds.
    warmup: {
      executor: 'constant-vus',
      vus: 10,
      duration: '10s',
      exec: 'request',
    },
    // Phase 2: real measurement. Starts AFTER warm-up (startTime),
    // with the same pattern as before (10 VUs, 100 iterations).
    measurement: {
      executor: 'per-vu-iterations',
      vus: 10,
      iterations: 10, // 10 VUs x 10 iterations = 100 total iterations
      startTime: '10s',
      exec: 'request',
    },
  },
  thresholds: {
    // NOTE: thresholds apply ONLY to the measurement scenario, so the
    // warm-up phase cannot contaminate the pass/fail result.
    'http_req_failed{scenario:measurement}': ['rate<0.01'],
    'http_req_duration{scenario:measurement}': ['p(95)<500'],
  },
};

export function request() {
  const res = http.get(URL, {
    headers: { 'eazybank-correlation-id': `k6-${__VU}-${__ITER}` },
  });
  check(res, {
    'status 200': (r) => r.status === 200,
    'body has accountsDto': (r) => r.body?.includes('accountsDto'),
  });
}
