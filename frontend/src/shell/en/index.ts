// SPDX-License-Identifier: Apache-2.0
import { app } from "./app";
import { console as consoleText } from "./console";

// Vietnamese sentence -> English, one table per area so that two people can fill them in at once.
export const en: Record<string, string> = { ...app, ...consoleText };
