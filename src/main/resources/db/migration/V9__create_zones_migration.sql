INSERT INTO zone (name) VALUES
    ('Zona Centro'),
    ('Zona Este'),
    ('Zona Oeste')
ON CONFLICT (name) DO NOTHING;

-- Asignar zona a los CPs (ajusta los CPs a tu catálogo real)
UPDATE zip_code SET zone_id = 1 WHERE code IN ('29001','29002','29003','29004','29005');
UPDATE zip_code SET zone_id = 2 WHERE code IN ('29010','29011','29012','29013','29014');
UPDATE zip_code SET zone_id = 3 WHERE code IN ('29006','29007','29008','29009','29015');