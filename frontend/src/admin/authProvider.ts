// SPDX-License-Identifier: Apache-2.0
import type { AuthProvider } from "react-admin";
import { getCurrentUser, signIn, signOut } from "../shell/auth";

// Sign-in is handled before the admin starts; this lets react-admin read the user and sign out.
const authProvider: AuthProvider = {
  login: async () => undefined,
  logout: async () => {
    await signOut();
  },
  checkAuth: async () => {
    if (!(await getCurrentUser())) {
      await signIn();
      throw new Error("Not signed in");
    }
  },
  checkError: async (error: { status?: number }) => {
    if (error?.status === 401) throw error;
  },
  getIdentity: async () => {
    const user = await getCurrentUser();
    if (!user) throw new Error("Not signed in");
    return { id: user.id, fullName: user.fullName };
  },
  getPermissions: async () => (await getCurrentUser())?.roles ?? [],
};

export default authProvider;
