-- MVP: una solicitud puede registrarse antes de asignarle zona, fecha y franja.
-- La programación posterior podrá completar estos campos sin alterar las bolsas.
ALTER TABLE collection ALTER COLUMN zone_id DROP NOT NULL;
ALTER TABLE collection ALTER COLUMN collection_date DROP NOT NULL;
ALTER TABLE collection ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE collection ALTER COLUMN end_time DROP NOT NULL;
