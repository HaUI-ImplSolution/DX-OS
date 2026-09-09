INSERT INTO diseases (code, name, description)
VALUES ('BROWN_SPOT', 'Đốm nâu', 'Bệnh đốm nâu trên lá lúa do nấm Bipolaris oryzae')
ON CONFLICT (code) DO NOTHING;

INSERT INTO pesticides (code, name, unit)
VALUES ('HEXACONAZOLE', 'Hexaconazole', 'lít/ha')
ON CONFLICT (code) DO NOTHING;

INSERT INTO treatment_rules (disease_id, pesticide_id, dose_amount, dose_unit, phi_days, notes, source, version)
SELECT d.id, p.id, 1.0, 'lít/ha', 14,
       'Phun khi phát hiện triệu chứng, tuân thủ liều khuyến cáo VietGAP',
       'VietGAP', 'v1'
FROM diseases d, pesticides p
WHERE d.code = 'BROWN_SPOT' AND p.code = 'HEXACONAZOLE'
  AND NOT EXISTS (
    SELECT 1 FROM treatment_rules tr WHERE tr.disease_id = d.id AND tr.pesticide_id = p.id
  );
