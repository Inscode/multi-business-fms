-- Where each number run actually starts.
--
-- The "not entered" gap check walks from the lowest number on record, which drags in
-- years of legacy numbering nobody is going to chase. These floors mark where the books
-- currently in use begin; anything below is left alone.
--
-- Stored rather than hardcoded so the boundary can move without a release.

INSERT INTO inv_settings (setting_key, value) VALUES
    ('bill_seq_floor_RAINCO_SYSTEM',     '13620'),
    ('bill_seq_floor_RAINCO_MANUAL',     '385'),
    ('bill_seq_floor_STATIONERY_SYSTEM', '764'),
    -- BK- is one shared physical book across Plastic, Stationery and Rainco.
    ('bill_seq_floor_SHARED_BOOK',       '335')
ON CONFLICT (setting_key) DO UPDATE SET value = EXCLUDED.value;
