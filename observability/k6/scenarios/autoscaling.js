import { check } from 'k6';
import {
  postEmergency,
  seedResources,
  setupUsers,
} from '../helpers/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:30080';

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '5m',
  scenarios: {
    autoscaling_spike: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.START_RATE || 10),
      timeUnit: '1s',
      stages: [
        { duration: __ENV.RAMP_UP || '1m', target: Number(__ENV.PEAK_RATE || 100) },
        { duration: __ENV.SUSTAIN || '3m', target: Number(__ENV.PEAK_RATE || 100) },
        { duration: __ENV.RAMP_DOWN || '1m', target: 0 },
      ],
      preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 200),
      maxVUs: Number(__ENV.MAX_VUS || 500),
    },
  },
};

export function setup() {
  const users = setupUsers(BASE_URL);
  seedResources(BASE_URL, users.adminToken);
  return users;
}

export default function (data) {
  const res = postEmergency(BASE_URL, data.dispatcherToken, 'autoscaling');
  check(res, {
    'emergency accepted': (r) => r.status === 200,
  });
}
