-- SPDX-License-Identifier: Apache-2.0
-- The last sequence number given to a record code. One row for each code field and period (the year
-- when the format has {year}, '-' when it has not); the row is locked while a record is created, so
-- two records never take the same number and a record that is not saved does not use one.

CREATE TABLE record_counter (
  model      TEXT   NOT NULL,
  entity     TEXT   NOT NULL,
  field      TEXT   NOT NULL,
  period     TEXT   NOT NULL,
  last_value BIGINT NOT NULL,
  PRIMARY KEY (model, entity, field, period)
);
