-- Free-text location for a ticket whose room is not in the room catalogue.
ALTER TABLE tickets ADD COLUMN room_note character varying(255);
