# Target DB Contract — Stage 3

## Pesticide

Giữ model hiện tại:
- id UUID
- name
- active_ingredient
- concentration
- notes

## TreatmentRule

Target:
- id UUID
- disease_id FK
- pesticide_id FK
- dose_min Float
- dose_max Float
- dose_unit String
- phi_days Integer
- source String
- version String
- notes Text

## DiseaseManagementPolicy

Target:
- id UUID
- disease_id FK UNIQUE
- management_status String
- direct_treatment_rule_allowed Boolean
- chemical_rule_scope String
- source String
- version String
- notes Text

## TreatmentApplication

Giữ:
- dose Float

`dose` ở đây là actual applied dose, không phải rule range.
