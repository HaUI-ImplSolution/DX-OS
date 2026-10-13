-- Starter rice variety catalogue for the Mekong Delta. Growth days are rounded public figures
-- (sowing to harvest); every row stays IN_REVIEW until an agronomist confirms it.
INSERT INTO rice_varieties (code, name, growth_days_min, growth_days_max, source, review_status)
VALUES
  ('OM5451', 'OM 5451', 95, 100, 'Viện Lúa ĐBSCL, thông tin công khai; chưa được kỹ sư xác nhận', 'IN_REVIEW'),
  ('OM18', 'OM 18', 95, 100, 'Viện Lúa ĐBSCL, thông tin công khai; chưa được kỹ sư xác nhận', 'IN_REVIEW'),
  ('OM4900', 'OM 4900', 100, 105, 'Viện Lúa ĐBSCL, thông tin công khai; chưa được kỹ sư xác nhận', 'IN_REVIEW'),
  ('OM6976', 'OM 6976', 95, 100, 'Viện Lúa ĐBSCL, thông tin công khai; chưa được kỹ sư xác nhận', 'IN_REVIEW'),
  ('IR50404', 'IR 50404', 90, 95, 'Viện Lúa ĐBSCL, thông tin công khai; chưa được kỹ sư xác nhận', 'IN_REVIEW'),
  ('JASMINE85', 'Jasmine 85', 90, 100, 'Viện Lúa ĐBSCL, thông tin công khai; chưa được kỹ sư xác nhận', 'IN_REVIEW'),
  ('ST24', 'ST24', 100, 105, 'Sở NN&PTNT Sóc Trăng, thông tin công khai; chưa được kỹ sư xác nhận', 'IN_REVIEW'),
  ('ST25', 'ST25', 100, 105, 'Sở NN&PTNT Sóc Trăng, thông tin công khai; chưa được kỹ sư xác nhận', 'IN_REVIEW'),
  ('DAITHOM8', 'Đài Thơm 8', 100, 105, 'Thông tin công khai của các công ty giống; chưa được kỹ sư xác nhận', 'IN_REVIEW');
