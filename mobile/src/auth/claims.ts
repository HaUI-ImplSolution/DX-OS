export type Role = "FARMER" | "ENGINEER" | "ADMIN";

export interface CurrentUser {
  username: string;
  fullName: string;
  roles: string[];
}

function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const base64 = jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(base64);
  const utf8 = decodeURIComponent(
    binary
      .split("")
      .map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, "0")}`)
      .join(""),
  );
  return JSON.parse(utf8);
}

// realm_access and name are claims of the access token, the same ones the backend reads.
export function userFromAccessToken(accessToken: string): CurrentUser {
  const claims = decodeJwtPayload(accessToken);
  const realmAccess = claims.realm_access as { roles?: string[] } | undefined;
  const username = (claims.preferred_username as string | undefined) ?? "";
  return {
    username,
    fullName: (claims.name as string | undefined) ?? username,
    roles: realmAccess?.roles ?? [],
  };
}

export const isFarmer = (user: CurrentUser) => user.roles.includes("FARMER");
