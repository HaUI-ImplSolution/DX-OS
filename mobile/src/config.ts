const required = (name: string, value: string | undefined): string => {
  if (!value) throw new Error(`Thiếu biến môi trường ${name}, xem mobile/.env.example`);
  return value.replace(/\/$/, "");
};

export const API_BASE_URL = required("EXPO_PUBLIC_API_BASE_URL", process.env.EXPO_PUBLIC_API_BASE_URL);
export const KEYCLOAK_URL = required("EXPO_PUBLIC_KEYCLOAK_URL", process.env.EXPO_PUBLIC_KEYCLOAK_URL);
export const KEYCLOAK_CLIENT_ID = required(
  "EXPO_PUBLIC_KEYCLOAK_CLIENT_ID",
  process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID,
);

// Largest GPS error (meters) used until the backend manifest reports its own limit.
export const GPS_MAX_ACCURACY_METERS = 30;
