import { check, sleep } from 'k6';
import {
  postEmergency,
  seedResources,
  setupUsers,
} from '../helpers/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:30080';

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '3m',
  scenarios: {
    hospital_failure: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '3m',
    },
  },
};

export function setup() {
  const users = setupUsers(BASE_URL);
  seedResources(BASE_URL, users.adminToken);
  return users;
}

export default function (data) {
  const res = postEmergency(BASE_URL, data.dispatcherToken, 'hospital-failure');
  check(res, {
    'emergency accepted': (r) => r.status === 200,
  });
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
