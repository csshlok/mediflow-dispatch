import { check } from 'k6';
import {
  postEmergency,
  seedResources,
  setupUsers,
} from '../helpers/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:30080';
const RATE = Number(__ENV.RATE || 20);
const DURATION = __ENV.DURATION || '2m';

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '3m',
  scenarios: {
    high_load: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 250),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  const users = setupUsers(BASE_URL);
  seedResources(BASE_URL, users.adminToken);
  return users;
}

export default function (data) {
  const res = postEmergency(BASE_URL, data.dispatcherToken, 'high-traffic');
  check(res, {
    'emergency accepted': (r) => r.status === 200,
  });
}
