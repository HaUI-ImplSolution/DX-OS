import * as Location from "expo-location";

export interface GpsFix {
  latitude: number;
  longitude: number;
  // Reported error radius in meters.
  accuracyMeters: number;
  // The phone says the position comes from a mock-location app (Android only).
  mocked: boolean;
}

export class GpsPermissionError extends Error {}

export async function readGpsFix(): Promise<GpsFix> {
  const permission = await Location.requestForegroundPermissionsAsync();
  if (!permission.granted) throw new GpsPermissionError("Chưa có quyền dùng vị trí.");
  const position = await Location.getCurrentPositionAsync({
    accuracy: Location.LocationAccuracy.High,
  });
  return {
    latitude: Number(position.coords.latitude.toFixed(6)),
    longitude: Number(position.coords.longitude.toFixed(6)),
    accuracyMeters: Number((position.coords.accuracy ?? 9999).toFixed(2)),
    mocked: position.mocked === true,
  };
}
