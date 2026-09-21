-- Canonical 38-class CV contract plus source-reviewed MVP treatment facts.
INSERT INTO diseases (code, name, description)
VALUES
  ('Apple___Apple_scab', 'Bệnh ghẻ táo', NULL),
  ('Apple___Black_rot', 'Bệnh thối đen trên táo', NULL),
  ('Apple___Cedar_apple_rust', 'Bệnh gỉ sắt tuyết tùng trên táo', NULL),
  ('Apple___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Blueberry___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Cherry_(including_sour)___Powdery_mildew', 'Bệnh phấn trắng trên anh đào', NULL),
  ('Cherry_(including_sour)___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot', 'Bệnh đốm lá Cercospora / đốm lá xám trên ngô', NULL),
  ('Corn_(maize)__Common_rust', 'Bệnh gỉ sắt thông thường trên ngô', NULL),
  ('Corn_(maize)___Northern_Leaf_Blight', 'Bệnh cháy lá phương Bắc trên ngô', NULL),
  ('Corn_(maize)___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Grape___Black_rot', 'Bệnh thối đen trên nho', NULL),
  ('Grape___Esca_(Black_Measles)', 'Bệnh Esca (sởi đen) trên nho', NULL),
  ('Grape___Leaf_blight_(Isariopsis_Leaf_Spot)', 'Bệnh cháy lá / đốm lá Isariopsis trên nho', NULL),
  ('Grape___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Orange___Haunglongbing_(Citrus_greening)', 'Bệnh vàng lá gân xanh (Greening) trên cam', NULL),
  ('Peach___Bacterial_spot', 'Bệnh đốm vi khuẩn trên đào', NULL),
  ('Peach___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Pepper,_bell___Bacterial_spot', 'Bệnh đốm vi khuẩn trên ớt chuông', NULL),
  ('Pepper,_bell___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Potato___Early_blight', 'Bệnh cháy lá sớm trên khoai tây', NULL),
  ('Potato___Late_blight', 'Bệnh cháy lá muộn trên khoai tây', NULL),
  ('Potato___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Raspberry___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Soybean___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Squash___Powdery_mildew', 'Bệnh phấn trắng trên bí', NULL),
  ('Strawberry___Leaf_scorch', 'Bệnh cháy lá trên dâu tây', NULL),
  ('Strawberry___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL),
  ('Tomato___Bacterial_spot', 'Bệnh đốm vi khuẩn trên cà chua', NULL),
  ('Tomato___Early_blight', 'Bệnh cháy lá sớm trên cà chua', NULL),
  ('Tomato___Late_blight', 'Bệnh cháy lá muộn trên cà chua', NULL),
  ('Tomato___Leaf_Mold', 'Bệnh mốc lá trên cà chua', NULL),
  ('Tomato___Septoria_leaf_spot', 'Bệnh đốm lá Septoria trên cà chua', NULL),
  ('Tomato___Spider_mites Two-spotted_spider_mite', 'Bệnh do nhện đỏ hai chấm trên cà chua', NULL),
  ('Tomato___Target_Spot', 'Bệnh đốm vòng / đốm mục tiêu trên cà chua', NULL),
  ('Tomato___Tomato_Yellow_Leaf_Curl_Virus', 'Bệnh virus xoăn vàng lá cà chua', NULL),
  ('Tomato___Tomato_mosaic_virus', 'Bệnh virus khảm cà chua', NULL),
  ('Tomato___healthy', 'Không phát hiện bệnh thuộc lớp CV (healthy)', NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO pesticides (code, name, unit)
VALUES
  ('REVUS_OPTI_440SC', 'Revus Opti 440SC', 'L/ha'),
  ('ORTIVA_600SC', 'Ortiva 600SC', 'L/ha'),
  ('AMISTAR_250SC', 'Amistar 250SC', 'L/ha'),
  ('AMISTAR_TOP_325SC', 'Amistar Top 325SC', 'L/ha')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    unit = EXCLUDED.unit;

-- Eight source-reviewed MVP treatment rules.
INSERT INTO treatment_rules (
  disease_id, pesticide_id, dose_min, dose_max, dose_unit,
  phi_days, notes, source, version
)
SELECT
  d.id,
  p.id,
  seed.dose_min,
  seed.dose_max,
  seed.dose_unit,
  seed.phi_days,
  seed.notes,
  seed.source,
  seed.version
FROM (VALUES
  ('Tomato___Late_blight', 'REVUS_OPTI_440SC', 2.00::numeric, 2.50::numeric, 'L/ha', 5,
   'MVP source-reviewed 2026-09-10; registered target: Sương mai. Application guidance: Phun khi tỷ lệ bệnh xuất hiện. Water volume: 600-1000 L/ha.',
   'S27,S28,S29,S38', '2026.09.10-mvp-reviewed-v1'),
  ('Potato___Early_blight', 'REVUS_OPTI_440SC', 2.00::numeric, 2.50::numeric, 'L/ha', 5,
   'MVP source-reviewed 2026-09-10; registered target: Đốm vòng. Application guidance: Phun khi tỷ lệ bệnh xuất hiện. Water volume: 400-500 L/ha.',
   'S27,S28,S29,S39', '2026.09.10-mvp-reviewed-v1'),
  ('Potato___Late_blight', 'REVUS_OPTI_440SC', 2.00::numeric, 2.50::numeric, 'L/ha', 5,
   'MVP source-reviewed 2026-09-10; registered target: Mốc sương. Application guidance: Phun khi tỷ lệ bệnh xuất hiện. Water volume: 400-500 L/ha.',
   'S27,S28,S29,S40', '2026.09.10-mvp-reviewed-v1'),
  ('Tomato___Early_blight', 'ORTIVA_600SC', 1.50::numeric, 1.50::numeric, 'L/ha', 7,
   'MVP source-reviewed 2026-09-10; registered target: Đốm vòng. Application guidance: Phun khi tỷ lệ bệnh khoảng 5-10%. Water volume: 400-600 L/ha.',
   'S27,S30,S29,S39', '2026.09.10-mvp-reviewed-v1'),
  ('Tomato___Late_blight', 'ORTIVA_600SC', 1.50::numeric, 1.50::numeric, 'L/ha', 7,
   'MVP source-reviewed 2026-09-10; registered target: Sương mai. Application guidance: Phun khi tỷ lệ bệnh khoảng 5-10%. Water volume: 400-600 L/ha.',
   'S27,S30,S29,S38', '2026.09.10-mvp-reviewed-v1'),
  ('Corn_(maize)___Northern_Leaf_Blight', 'AMISTAR_TOP_325SC', 0.25::numeric, 0.50::numeric, 'L/ha', 10,
   'MVP source-reviewed 2026-09-10; registered target: Đốm lá lớn. Application guidance: Phun khi bệnh xuất hiện, tỷ lệ bệnh khoảng 5%.',
   'S27,S32,S29,S41', '2026.09.10-mvp-reviewed-v1'),
  ('Tomato___Early_blight', 'AMISTAR_250SC', 0.30::numeric, 0.40::numeric, 'L/ha', 5,
   'MVP source-reviewed 2026-09-10; registered target: Đốm vòng. Application guidance: Phun khi bệnh chớm xuất hiện hoặc tỷ lệ bệnh khoảng 5%. Water volume: 500-600 L/ha.',
   'S27,S31,S33,S29,S39', '2026.09.10-mvp-reviewed-v1'),
  ('Potato___Late_blight', 'AMISTAR_250SC', 0.50::numeric, 0.50::numeric, 'L/ha', 14,
   'MVP source-reviewed 2026-09-10; registered target: Mốc sương. Application guidance: Phun khi bệnh chớm xuất hiện hoặc tỷ lệ bệnh khoảng 5%. Water volume: 500-600 L/ha.',
   'S27,S31,S33,S29,S40', '2026.09.10-mvp-reviewed-v1')
) AS seed(
  disease_code, pesticide_code, dose_min, dose_max, dose_unit,
  phi_days, notes, source, version
)
JOIN diseases d ON d.code = seed.disease_code
JOIN pesticides p ON p.code = seed.pesticide_code
WHERE NOT EXISTS (
  SELECT 1
  FROM treatment_rules tr
  WHERE tr.disease_id = d.id
    AND tr.pesticide_id = p.id
);
