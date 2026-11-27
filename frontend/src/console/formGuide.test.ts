// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { describeSchema, GUIDE, SCHEMA_HELP } from "./formGuide";

describe("the guide of the model form", () => {
  it("says something for every part of the file", () => {
    Object.entries(GUIDE).forEach(([section, guide]) => {
      expect(guide.summary, section).not.toBe("");
      expect(guide.accepts.length, section).toBeGreaterThan(0);
      expect(guide.extends.length, section).toBeGreaterThan(0);
      expect(guide.creates.length, section).toBeGreaterThan(0);
    });
  });
});

describe("describeSchema", () => {
  const key = Object.keys(SCHEMA_HELP).find((name) => !name.includes("."))!;

  it("puts the help of a key on its property as the description", () => {
    const schema = { type: "object", properties: { [key]: { type: "string" }, other: { type: "string" } } };
    const result = describeSchema(schema) as { properties: Record<string, { description?: string }> };
    expect(result.properties[key].description).toBe(SCHEMA_HELP[key]);
    expect(result.properties.other.description).toBeUndefined();
  });

  it("follows the path of nested properties, arrays and alternatives", () => {
    const nested = Object.keys(SCHEMA_HELP).find((name) => name.split(".").length === 2)!;
    const [outer, inner] = nested.split(".");
    const schema = {
      properties: { [outer]: { oneOf: [{ items: { properties: { [inner]: { type: "string" } } } }] } },
    };
    const result = describeSchema(schema) as { properties: Record<string, { oneOf: { items: { properties: Record<string, { description?: string }> } }[] }> };
    expect(result.properties[outer].oneOf[0].items.properties[inner].description).toBe(SCHEMA_HELP[nested]);
  });

  it("does not change the schema it is given", () => {
    const schema = { properties: { [key]: { type: "string" } } };
    describeSchema(schema);
    expect(schema).toEqual({ properties: { [key]: { type: "string" } } });
  });

  it("returns a value that is not an object as it is", () => {
    expect(describeSchema({ type: "string" })).toEqual({ type: "string" });
  });
});
