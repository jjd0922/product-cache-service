import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HOT_PRODUCT_ID = Number(__ENV.HOT_PRODUCT_ID || '1');
const COLD_ID_START = Number(__ENV.COLD_ID_START || '2');
const COLD_ID_END = Number(__ENV.COLD_ID_END || '10000');
const HOT_RATIO = Number(__ENV.HOT_RATIO || '0.9');
const TARGET_RPS = Number(__ENV.TARGET_RPS || '1000');
const RAMP_UP = __ENV.RAMP_UP || '5m';
const STEADY = __ENV.STEADY || '1m';

export const options = {
  scenarios: {
    hot_cold_lookup: {
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
  const productId = chooseProductId();
  const response = http.get(`${BASE_URL}/products/${productId}`, {
    tags: {
      scenario_name: 'hot_cold_lookup',
      key_type: productId === HOT_PRODUCT_ID ? 'hot' : 'cold',
    },
  });

  check(response, {
    'hot-cold status is 200': (res) => res.status === 200,
  });
}

function chooseProductId() {
  const slot = exec.scenario.iterationInTest % 100;
  if (slot < HOT_RATIO * 100) {
    return HOT_PRODUCT_ID;
  }

  const range = COLD_ID_END - COLD_ID_START + 1;
  return COLD_ID_START + (exec.scenario.iterationInTest % range);
}
