// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { loadClassifiers } from '../dist/classifier/index.js';

const fixture = JSON.parse(readFileSync(new URL('./fixtures/classifier.json', import.meta.url), 'utf8'));
const classifiers = loadClassifiers(fileURLToPath(new URL('../../tools/classifier/model/', import.meta.url)));

for (const [name, { sha256, cases }] of Object.entries(fixture)) {
  test(`${name}: ${cases.length} classifier results match Python`, t => {
    const classifier = classifiers.get(name);
    assert.equal(classifier.revision, sha256, 'regenerate the Python fixture after replacing weights');
    let maxDifference = 0;
    for (const { id, inputs, label, probability, evidence } of cases) {
      const actual = classifier.predict(inputs);
      assert.equal(actual.label, label, id);
      const difference = Math.abs(actual.probability - probability);
      assert.ok(difference <= 1e-9, `${id}: ${actual.probability} vs ${probability}`);
      assert.deepEqual(actual.evidence, evidence, id);
      maxDifference = Math.max(maxDifference, difference);
    }
    t.diagnostic(`Maximum probability difference: ${maxDifference}`);
  });
}
