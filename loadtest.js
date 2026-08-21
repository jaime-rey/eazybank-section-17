import http from 'k6/http';
import { check } from 'k6';

const URL = 'http://localhost:8072/eazybank/accounts/api/fetchCustomerDetails?mobileNumber=9345432123';

export const options = {
  scenarios: {
    // Fase 1: calentamiento. Abre conexiones, deja que la JVM y los pools
    // se "calienten". Sus resultados NO cuentan en los thresholds finales.
    calentamiento: {
      executor: 'constant-vus',
      vus: 10,
      duration: '10s',
      exec: 'peticion',
    },
    // Fase 2: medición real. Arranca DESPUÉS del calentamiento (startTime),
    // con el mismo patrón que ya tenías (10 VUs, 100 iteraciones).
    medicion: {
      executor: 'per-vu-iterations',
      vus: 10,
      iterations: 10, // 10 VUs x 10 iteraciones = 100 iteraciones totales, igual que antes
      startTime: '10s',
      exec: 'peticion',
    },
  },
  thresholds: {
    // OJO: aplicamos el threshold SOLO al escenario de medición,
    // para que el calentamiento no lo contamine.
    'http_req_failed{scenario:medicion}': ['rate<0.01'],
    'http_req_duration{scenario:medicion}': ['p(95)<500'],
  },
};

export function peticion() {
  const res = http.get(URL, {
    headers: { 'eazybank-correlation-id': `k6-${__VU}-${__ITER}` },
  });
  check(res, {
    'status 200': (r) => r.status === 200,
    'body has accountsDto': (r) => r.body?.includes('accountsDto'),
  });
}
