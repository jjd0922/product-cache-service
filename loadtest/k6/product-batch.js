import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID_START = Number(__ENV.PRODUCT_ID_START || '1');
const PRODUCT_ID_END = Number(__ENV.PRODUCT_ID_END || '10000');
const BATCH_SIZE = Number(__ENV.BATCH_SIZE || '20');
const TARGET_RPS = Number(__ENV.TARGET_RPS || '500');
const RAMP_UP = __ENV.RAMP_UP || '5m';
const STEADY = __ENV.STEADY || '1m';

export const options = {
  scenarios: {
    batch_product_lookup: {
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
  const ids = batchIds();
  const response = http.post(
    `${BASE_URL}/products/ids`,
    JSON.stringify({ ids }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { scenario_name: 'batch_product_lookup' },
    }
  );

  check(response, {
    'batch status is 200': (res) => res.status === 200,
  });
}

function batchIds() {
  const ids = [];
  const range = PRODUCT_ID_END - PRODUCT_ID_START + 1;
  const offset = exec.scenario.iterationInTest * BATCH_SIZE;

  for (let i = 0; i < BATCH_SIZE; i += 1) {
    ids.push(PRODUCT_ID_START + ((offset + i) % range));
  }
  return ids;
}
