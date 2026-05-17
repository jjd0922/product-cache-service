import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID_START = Number(__ENV.PRODUCT_ID_START || '1');
const PRODUCT_ID_END = Number(__ENV.PRODUCT_ID_END || '10000');
const TARGET_RPS = Number(__ENV.TARGET_RPS || '1000');
const RAMP_UP = __ENV.RAMP_UP || '5m';
const STEADY = __ENV.STEADY || '1m';

export const options = {
  scenarios: {
    single_product_lookup: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || '200'),
      maxVUs: Number(__ENV.MAX_VUS || '1000'),
      stages: [
        { duration: RAMP_UP, target: TARGET_RPS },
        { duration: STEADY, target: TARGET_RPS },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
  const productId = randomProductId();
  const response = http.get(`${BASE_URL}/products/${productId}`, {
    tags: { scenario_name: 'single_product_lookup' },
  });

  check(response, {
    'single status is 200': (res) => res.status === 200,
  });
}

function randomProductId() {
  const range = PRODUCT_ID_END - PRODUCT_ID_START + 1;
  return PRODUCT_ID_START + (exec.scenario.iterationInTest % range);
}
