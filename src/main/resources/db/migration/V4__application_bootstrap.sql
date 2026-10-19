ALTER TABLE zone
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE SEQUENCE IF NOT EXISTS seq_collection_code START WITH 1001;
CREATE SEQUENCE IF NOT EXISTS seq_bag_code START WITH 1001;
CREATE SEQUENCE IF NOT EXISTS seq_incident_code START WITH 2001;
CREATE SEQUENCE IF NOT EXISTS seq_withdrawal_code START WITH 501;

INSERT INTO incident_type (code, name) VALUES
    ('CIUDADANO_AUSENTE', 'Ciudadano ausente'),
    ('BOLSA_NO_ENCONTRADA', 'Bolsa no encontrada'),
    ('DISCREPANCIA', 'Discrepancia de contenido'),
    ('OTRO', 'Otro')
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_parameter (param_key, param_value) VALUES
    ('importe_provisional', 'true'),
    ('nave_nombre', 'Centro de operaciones'),
    ('nave_lat', '36.7213'),
    ('nave_lng', '-4.4214'),
    ('velocidad_media_kmh', '25'),
    ('minutos_por_parada', '10')
ON CONFLICT (param_key) DO NOTHING;

INSERT INTO zip_code (code, neighbourhood, lat, lng) VALUES
    ('29001', 'Málaga', 36.716600, -4.425000),
    ('29002', 'Málaga', 36.711000, -4.428000),
    ('29003', 'Málaga', 36.704000, -4.449000),
    ('29004', 'Málaga', 36.687000, -4.466000),
    ('29005', 'Málaga', 36.720000, -4.424000),
    ('29006', 'Málaga', 36.705000, -4.469000),
    ('29007', 'Málaga', 36.728000, -4.437000),
    ('29008', 'Málaga', 36.723000, -4.421000),
    ('29009', 'Málaga', 36.730000, -4.431000),
    ('29010', 'Málaga', 36.723000, -4.445000),
    ('29011', 'Málaga', 36.735000, -4.428000),
    ('29012', 'Málaga', 36.725000, -4.410000),
    ('29013', 'Málaga', 36.740000, -4.420000),
    ('29014', 'Málaga', 36.715000, -4.480000),
    ('29015', 'Málaga', 36.730000, -4.415000),
    ('29016', 'Málaga', 36.720000, -4.385000),
    ('29017', 'Málaga', 36.720000, -4.350000),
    ('29018', 'Málaga', 36.750000, -4.340000)
ON CONFLICT (code) DO NOTHING;
