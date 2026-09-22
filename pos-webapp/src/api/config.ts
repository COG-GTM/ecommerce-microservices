export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8181';

export const USE_MOCK_DATA: boolean =
  (import.meta.env.VITE_USE_MOCK ?? 'true').toLowerCase() !== 'false';

export const AUTH_TOKEN: string | undefined = import.meta.env.VITE_API_TOKEN;

export const STORE_ID = '1969';
export const REGISTER_ID = '04';
export const TAX_RATE = 0.08625;
