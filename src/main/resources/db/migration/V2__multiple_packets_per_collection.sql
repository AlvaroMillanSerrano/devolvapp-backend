-- Move the relationship from collection -> one packet to packet -> one collection.
ALTER TABLE packet
    ADD COLUMN collection_id BIGINT;

UPDATE packet p
SET collection_id = c.id
FROM collection c
WHERE c.packet_id = p.id;

ALTER TABLE packet
    ALTER COLUMN collection_id SET NOT NULL,
    ADD CONSTRAINT fk_packet_collection
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE collection
    DROP CONSTRAINT fk_collection_packet,
    DROP COLUMN packet_id;

ALTER TABLE packet
    ADD CONSTRAINT chk_packet_container_type
    CHECK (
        COALESCE(
            container->>'type' IN ('PET', 'LATA', 'BRIK', 'VIDRIO', 'MEZCLADO'),
            FALSE
        )
    );

CREATE INDEX idx_packet_collection ON packet(collection_id);
