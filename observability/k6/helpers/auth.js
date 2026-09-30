import http from 'k6/http';
import { check, sleep } from 'k6';

export const DEFAULT_PASSWORD = 'password123';

export function unique(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000000000)}`;
}

export function jsonHeaders(extra = {}) {
  return {
    'Content-Type': 'application/json',
    ...extra,
  };
}

export function authHeaders(token, extra = {}) {
  return jsonHeaders({
    Authorization: `Bearer ${token}`,
    ...extra,
  });
}

export function registerUser(baseUrl, role, emailPrefix) {
  const email = `${unique(emailPrefix)}@mediflow.test`;
  const body = JSON.stringify({
    name: `${role} Load Test User`,
    email,
    password: DEFAULT_PASSWORD,
    role,
  });

  const res = http.post(`${baseUrl}/api/auth/register`, body, {
    headers: jsonHeaders(),
  });

  check(res, {
    [`${role} registration accepted`]: (r) => r.status === 201 || r.status === 409,
  });

  return { email, password: DEFAULT_PASSWORD };
}

export function login(baseUrl, credentials) {
  const res = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({
      email: credentials.email,
      password: credentials.password,
    }),
    { headers: jsonHeaders() },
  );

  check(res, {
    'login successful': (r) => r.status === 200 && !!JSON.parse(r.body).token,
  });

  return JSON.parse(res.body).token;
}

export function setupUsers(baseUrl) {
  const admin = registerUser(baseUrl, 'ADMIN', 'admin-load');
  const dispatcher = registerUser(baseUrl, 'DISPATCHER', 'dispatcher-load');

  return {
    adminToken: login(baseUrl, admin),
    dispatcherToken: login(baseUrl, dispatcher),
  };
}

export function seedResources(baseUrl, adminToken, options = {}) {
  const ambulanceCount = Number(options.ambulances || __ENV.SEED_AMBULANCES || 25);
  const hospitalBeds = Number(options.hospitalBeds || __ENV.SEED_HOSPITAL_BEDS || 5000);
  const suffix = unique('seed');

  const hospitalRes = http.post(
    `${baseUrl}/api/hospitals`,
    JSON.stringify({
      hospitalName: `Load Test Hospital ${suffix}`,
      availableBeds: hospitalBeds,
      latitude: 28.615,
      longitude: 77.21,
    }),
    {
      headers: authHeaders(adminToken, {
        'Idempotency-Key': `hospital-${suffix}`,
      }),
    },
  );

  check(hospitalRes, {
    'hospital seeded': (r) => r.status === 201 || r.status === 409,
  });

  for (let i = 0; i < ambulanceCount; i += 1) {
    const id = `${suffix}-${i}`;
    const res = http.post(
      `${baseUrl}/api/ambulances`,
      JSON.stringify({
        registrationNumber: `LOAD-${id}`,
        capabilities: 'ALS',
        crewInfo: '2 Paramedics, 1 Driver',
        status: 'AVAILABLE',
      }),
      {
        headers: authHeaders(adminToken, {
          'Idempotency-Key': `ambulance-${id}`,
        }),
      },
    );

    check(res, {
      'ambulance seeded': (r) => r.status === 201 || r.status === 409,
    });
  }

  sleep(Number(__ENV.SEED_SETTLE_SECONDS || 10));
}

export function emergencyPayload(prefix = 'PAT-LOAD') {
  return JSON.stringify({
    patientId: unique(prefix),
    severity: 'HIGH',
    latitude: 28.615 + (Math.random() - 0.5) * 0.02,
    longitude: 77.21 + (Math.random() - 0.5) * 0.02,
  });
}

export function postEmergency(baseUrl, dispatcherToken, scenarioName) {
  return http.post(`${baseUrl}/api/emergency`, emergencyPayload(scenarioName), {
    headers: authHeaders(dispatcherToken, {
      'Idempotency-Key': unique(`emergency-${scenarioName}`),
    }),
  });
}
