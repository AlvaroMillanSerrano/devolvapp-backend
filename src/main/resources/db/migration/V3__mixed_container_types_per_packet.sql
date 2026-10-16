-- Convert the earlier single-type JSON shape to a per-type quantity map.
UPDATE packet
SET container = jsonb_build_object(container->>'type', container_quantity)
WHERE jsonb_typeof(container) = 'object'
  AND container ? 'type';

ALTER TABLE packet
    DROP CONSTRAINT chk_packet_container_type,
    ALTER COLUMN container_quantity TYPE BIGINT;

ALTER TABLE packet
    ADD CONSTRAINT chk_packet_container_contents
    CHECK (
        jsonb_typeof(container) = 'object'
        AND container <> '{}'::jsonb
        AND container - ARRAY['PET', 'LATA', 'BRIK', 'VIDRIO', 'MEZCLADO']::text[] = '{}'::jsonb
        AND NOT jsonb_path_exists(
            container,
            '$.* ? (@.type() != "number" || @ <= 0 || @ != @.floor())'
        )
    );
