-- ============================================================
-- V62: Seed the invoicing catalog from ghanim-wholesale.
-- Replays that system's seed migrations (V1 seed section, V4, V3,
-- V5, V6, V7, V9, V10, V11 — in that order) against the inv_ tables,
-- so both systems start from the same brands, slabs and items.
-- Stock quantities start at 0 — invoicing stock is tracked separately.
-- ============================================================

-- Source: ghanim-wholesale V1 (brand + slab seed section)
-- -------- RAINCO BRAND & SLAB DATA --------

INSERT INTO inv_brands (name, brand_code, category, principal, discount_type, default_margin_pct) VALUES
    ('Rainco', 'RC', 'RAINCO', 'RAINCO', 'SLAB', 18.00);

-- Rainco slab tiers
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id, 0,      9999.99,  0.00, 1 FROM inv_brands WHERE name = 'Rainco' UNION ALL
SELECT id, 10000,  49999.99, 5.00, 2 FROM inv_brands WHERE name = 'Rainco' UNION ALL
SELECT id, 50000,  99999.99, 7.50, 3 FROM inv_brands WHERE name = 'Rainco' UNION ALL
SELECT id, 100000, NULL,    10.00, 4 FROM inv_brands WHERE name = 'Rainco';

-- Stationery brand (NONE discount — stationery agent invoices have no slab)
INSERT INTO inv_brands (name, brand_code, category, principal, discount_type, default_margin_pct) VALUES
    ('Stationery Agent', NULL, 'STATIONERY', 'STATIONERY_AGENT', 'NONE', NULL);

-- Plastic brand (OWN — manual discount entered per invoice)
INSERT INTO inv_brands (name, brand_code, category, principal, discount_type, default_margin_pct) VALUES
    ('Plastic (Own)', NULL, 'PLASTIC', 'OWN', 'NONE', NULL);

-- ============================================================
-- Source: ghanim-wholesale V4 (plastic supplier brands)
-- V4 — Plastic supplier brands
-- All use discount_type = 'NONE' (plastic discount is manual per invoice)

INSERT INTO inv_brands (name, brand_code, category, principal, discount_type, default_margin_pct) VALUES
    ('NIPPON LANKA ROPES INDUSTRIES', 'NLR',  'PLASTIC', 'OWN', 'NONE', NULL),
    ('COLOMBO PRODUCTS',              'CP',   'PLASTIC', 'OWN', 'NONE', NULL),
    ('R.N.I INDUSTRIES',              'RNI',  'PLASTIC', 'OWN', 'NONE', NULL),
    ('Uniplast Lanka (PVT) LTD',      'UNI',  'PLASTIC', 'OWN', 'NONE', NULL),
    ('NIPPON PLASTIC INDUSTRIES',     'NPI',  'PLASTIC', 'OWN', 'NONE', NULL),
    ('RAINBOW',                       'RB',   'PLASTIC', 'OWN', 'NONE', NULL),
    ('JAYAMALI PLASTIC',              'JM',   'PLASTIC', 'OWN', 'NONE', NULL),
    ('SNS PLASTICS',                  'SNS',  'PLASTIC', 'OWN', 'NONE', NULL),
    ('MAV PLASTIC',                   'MAV',  'PLASTIC', 'OWN', 'NONE', NULL),
    ('RT&DK CONSUMERS(PVT)LTD',       'RTDK', 'PLASTIC', 'OWN', 'NONE', NULL);

-- ============================================================
-- Source: ghanim-wholesale V3 (stationery items)
-- V3 — Stationery items seed data
-- Uses subquery for brand_id so it is independent of insertion order.
-- All items temporarily placed under 'Stationery Agent'; V9 re-points them
-- to the correct sub-brand (Hauser / Socks / Lunch Box / Shoe Polish).

INSERT INTO inv_items (item_code, description, category, brand_id, mrp, margin_pct, wholesale_price, active, stock_qty) VALUES

-- ---- Lunch Box ----
('J7010', 'Junior lunch box-500ml',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   520.00, true, 0),
('J7011', 'Junior lunch box-700ml',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   630.00, true, 0),
('J7012', 'Junior lunch box-750ml',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   480.00, true, 0),

-- ---- Junior School Socks ----
('18600', 'School Socks White - S',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   300.00, true, 0),
('18610', 'School Socks White - CM',                   'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   310.00, true, 0),
('18620', 'School Socks White - M',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   320.00, true, 0),
('18630', 'School Socks White - L',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   330.00, true, 0),
('18601', 'School Socks White - S (Alt)',               'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   300.00, true, 0),
('18611', 'School Socks Black - CM',                   'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   310.00, true, 0),
('18621', 'School Socks Black - M',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   320.00, true, 0),
('18631', 'School Socks Black - L',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   330.00, true, 0),
('18730', 'Dirt Buster Socks - S',                     'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   320.00, true, 0),
('18731', 'Dirt Buster Socks - CM',                    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   330.00, true, 0),
('18732', 'Dirt Buster Socks - M',                     'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   350.00, true, 0),
('18733', 'Dirt Buster Socks - L',                     'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   360.00, true, 0),

-- ---- Cotton Max Office Socks ----
('18500', 'Office Mid Calf Socks - Plain Black',        'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   380.00, true, 0),
('18501', 'Office Mid Calf Socks - Plain Brown',        'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   380.00, true, 0),
('18502', 'Office Mid Calf Socks - Plain Grey',         'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   380.00, true, 0),
('18503', 'Office Mid Calf Socks - Plain Navy Blue',    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   380.00, true, 0),
('18510', 'Office Mid Calf Socks - Design Black',       'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   420.00, true, 0),
('18511', 'Office Mid Calf Socks - Design Brown',       'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   420.00, true, 0),
('18512', 'Office Mid Calf Socks - Design Gray',        'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   420.00, true, 0),
('18513', 'Office Mid Calf Socks - Design Navy Blue',   'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   420.00, true, 0),
('18400', 'Office Half Socks - Plain Black',            'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   360.00, true, 0),
('18401', 'Office Half Socks - Plain Brown',            'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   360.00, true, 0),
('18402', 'Office Half Socks - Plain Grey',             'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   360.00, true, 0),
('18403', 'Office Half Socks - Plain Navy Blue',        'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   360.00, true, 0),
('18410', 'Office Half Socks - Design Black',           'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   390.00, true, 0),
('18411', 'Office Half Socks - Design Brown',           'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   390.00, true, 0),
('18412', 'Office Half Socks - Design Grey',            'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   390.00, true, 0),
('18413', 'Office Half Socks - Design Navy Blue',       'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   390.00, true, 0),

-- ---- Pro Silver Shoe Care ----
('8950',  'Pro Silver Shoe Polish Wax - Black (36g)',    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   340.00, true, 0),
('8951',  'Pro Silver Shoe Polish Wax - Brown (36g)',    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   340.00, true, 0),
('8952',  'Pro Silver Shoe Polish Wax - White (36g)',    'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   340.00, true, 0),
('8953',  'Pro Silver Shoe Polish Liquid - Black (75ml)','STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   475.00, true, 0),
('8954',  'Pro Silver Shoe Polish Liquid - Black (40ml)','STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   280.00, true, 0),
('8955',  'Pro Silver Shoe Polish Liquid - White (75ml)','STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   475.00, true, 0),
('8956',  'Pro Silver Shoe Polish Liquid - White (40ml)','STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   280.00, true, 0),
('8957',  'Pro Silver Shoe Shine Brush',                 'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   300.00, true, 0),
('8958',  'Pro Silver Shoe Shine Brush - 2 in 1',        'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   360.00, true, 0),

-- ---- Hauser Stationery ----
('9000',  'Hauser Darkies - Graphite 10 Pcs',            'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   500.00, true, 0),
('9001',  'Hauser Graphite Pencil 12 Pcs',               'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   480.00, true, 0),
('9002',  'Hauser Tryo Neon - Polymer Pencils 30 Pcs',   'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   900.00, true, 0),
('9003',  'Hauser Graphite Pencils 50 Pcs',              'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,  2000.00, true, 0),
('9010',  'Hauser Small White Eraser 30 Pcs',            'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   750.00, true, 0),
('9011',  'Hauser Small Neon Erasers 30 Pcs',            'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   750.00, true, 0),
('9012',  'Hauser Large White Erasers 20 Pcs',           'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,  1000.00, true, 0),
('9013',  'Hauser Large Neon Erasers 20 Pcs',            'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,  1000.00, true, 0),
('9020',  'Hauser Shell Neon DX Sharpeners 36 Pcs',      'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,  1440.00, true, 0),
('9021',  'Hauser Beetle Sharpeners 36 Pcs',             'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,  1440.00, true, 0),
('9030',  'Hauser Color Pencils 6 Pcs',                  'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   270.00, true, 0),
('9031',  'Hauser Color Pencils 12 Pcs',                 'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   520.00, true, 0),
('9040',  'Hauser Oil Pastels 12 Pcs',                   'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   240.00, true, 0),
('9041',  'Hauser Oil Pastels 24 Pcs',                   'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   480.00, true, 0),
('9050',  'Hauser 6 Inch Ruler - 10 Pcs',                'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   400.00, true, 0),
('9051',  'Hauser 12 Inch Ruler - 10 Pcs',               'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   800.00, true, 0),
('9060',  'Hauser Neo Matrix Plastic Mathematical Set',   'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   700.00, true, 0),
('9061',  'Hauser XO Mate Metal Mathematical Set',        'STATIONERY', (SELECT id FROM inv_brands WHERE name='Stationery Agent'), NULL, NULL,   850.00, true, 0);

-- ============================================================
-- Source: ghanim-wholesale V5 (plastic items part 1)
-- V5 — All plastic items (595 total, all suppliers)
-- item_code format: {SUPPLIER_CODE}{4-digit sequential}
-- mrp = purchase price (cost ref), wholesale_price = selling price

INSERT INTO inv_items (item_code, description, category, brand_id, mrp, margin_pct, wholesale_price, active, stock_qty) VALUES

-- ---- NIPPON LANKA ROPES INDUSTRIES (NLR0001–NLR0015) ----
('NLR0001', '2MM Rope 100 Yards',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'),  350.00, -25.00,  262.50, true, 0),
('NLR0002', '3MM Rope 100 Yards',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'),  630.00, -25.00,  472.50, true, 0),
('NLR0003', '4MM Ropes 100 Yards',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'), 1130.00, -25.00,  847.50, true, 0),
('NLR0004', '5MM Rope 100 Yards',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'), 1505.00, -25.00, 1128.75, true, 0),
('NLR0005', '6MM Rope 100 Yards',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'), 2850.00, -25.00, 2137.50, true, 0),
('NLR0006', '7MM Rope 100 Yards',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'), 3800.00, -25.00, 2850.00, true, 0),
('NLR0007', '8MM Rope 100 Yards',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'), 5130.00, -25.00, 3847.50, true, 0),
('NLR0008', '9MM Rope 100 Yards',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'), 6050.00, -25.00, 4537.50, true, 0),
('NLR0009', '10MM Rope 100 Yards',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'), 8010.00, -25.00, 6007.50, true, 0),
('NLR0010', '12MM Rope 100 Yards',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'),11875.00, -25.00, 8906.25, true, 0),
('NLR0011', '14MM Rope 100 Yards',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'),15165.00, -25.00,11373.75, true, 0),
('NLR0012', '16MM Rope 100 Yards',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'),19080.00, -25.00,14310.00, true, 0),
('NLR0013', '18MM Rope 100 Yards',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'),27300.00, -25.00,20475.00, true, 0),
('NLR0014', '20MM Rope 100 Yards',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'),29980.00, -25.00,22485.00, true, 0),
('NLR0015', 'Drawer 6PCS',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON LANKA ROPES INDUSTRIES'), 5215.00,  10.00, 5736.50, true, 0),

-- ---- COLOMBO PRODUCTS (CP0001–CP0123) ----
('CP0001', 'Heater Jug (Evro)',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1900.00,  30.00,  2470.00, true, 0),
('CP0002', 'Blender (Sonali)',                           'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6100.00,  25.00,  7625.00, true, 0),
('CP0003', 'Only Cup (160cc)',                           'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  780.00,  25.00,   975.00, true, 0),
('CP0004', 'Glass Stove (Kawashi)',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 4450.00,  25.00,  5562.50, true, 0),
('CP0005', 'Rice Cooker 2.8L (Black Ford)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5250.00,  25.00,  6562.50, true, 0),
('CP0006', 'Rice Cooker 2.8L (Smart Home)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5250.00,  25.00,  6562.50, true, 0),
('CP0007', 'Rice Cooker 2.8 (Taiko)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 4500.00,  25.00,  5625.00, true, 0),
('CP0008', 'Electric Chopper (Kenvo)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 3200.00,  25.00,  4000.00, true, 0),
('CP0009', 'Kitchen Scale',                              'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  500.00,  25.00,   625.00, true, 0),
('CP0010', 'Grinder (Kawashi Sigma)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 8250.00,  25.00, 10312.50, true, 0),
('CP0011', 'My Home Set',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2250.00,  25.00,  2812.50, true, 0),
('CP0012', 'Whistling Kettle 2.5L (Zone)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2000.00,  25.00,  2500.00, true, 0),
('CP0013', 'Whistling Kettle 4L (Zone)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2400.00,  25.00,  3000.00, true, 0),
('CP0014', 'Whistling Kettle 1.5L (Zone)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1600.00,  25.00,  2000.00, true, 0),
('CP0015', 'Whistling Kettle 3L (Zone)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2200.00,  25.00,  2750.00, true, 0),
('CP0016', 'Table Cloth',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5900.00,  25.00,  7375.00, true, 0),
('CP0017', 'Table Cloth Transparent (09mm)',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6500.00,  25.00,  8125.00, true, 0),
('CP0018', 'Iron (Heavy)',                               'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2350.00,  25.00,  2937.50, true, 0),
('CP0019', 'Plastic Pot SQ (S) 7L',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  160.00,  25.00,   200.00, true, 0),
('CP0020', 'Plastic Pot SQ (M) 11L',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  240.00,  25.00,   300.00, true, 0),
('CP0021', 'Plastic Pot SQ (L) 14L',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  380.00,  25.00,   475.00, true, 0),
('CP0022', 'Iron Board',                                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 3250.00,  25.00,  4062.50, true, 0),
('CP0023', 'Super Rake',                                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2500.00,  25.00,  3125.00, true, 0),
('CP0024', 'Can 5Ltr',                                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  190.00,  25.00,   237.50, true, 0),
('CP0025', 'Toaster',                                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2400.00,  25.00,  3000.00, true, 0),
('CP0026', 'Fan (Kawashi)',                              'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6900.00,  25.00,  8625.00, true, 0),
('CP0027', 'Fan (Metro)',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6900.00,  25.00,  8625.00, true, 0),
('CP0028', 'Kerosene Stove',                             'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2750.00,  25.00,  3437.50, true, 0),
('CP0029', '3x6 Mat',                                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  290.00,  20.00,   348.00, true, 0),
('CP0030', '6x9 Mat',                                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1250.00,  20.00,  1500.00, true, 0),
('CP0031', '6x9 Mat (Poonam)',                           'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1250.00,  20.00,  1500.00, true, 0),
('CP0032', '9x12 Mat',                                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2460.00,  20.00,  2952.00, true, 0),
('CP0033', 'Wood Wangadi No-08',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  680.00,  20.00,   816.00, true, 0),
('CP0034', 'Master Rack',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 4000.00, -12.50,  3500.00, true, 0),
('CP0035', 'Silver Lunch Box',                           'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  380.00,   0.00,   380.00, true, 0),
('CP0036', 'Mob (S)',                                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  180.00,  25.00,   225.00, true, 0),
('CP0037', 'Broom',                                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  280.00,  20.00,   336.00, true, 0),
('CP0038', 'Hand Brush',                                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   65.00,  25.00,    81.25, true, 0),
('CP0039', 'U Bag',                                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  180.00,  25.00,   225.00, true, 0),
('CP0040', 'Mob (Medium)',                               'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  780.00,  25.00,   975.00, true, 0),
('CP0041', 'Black Kettle No-07',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1250.00,  15.00,  1437.50, true, 0),
('CP0042', 'Black Kettle No-06',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  925.00,  20.00,  1110.00, true, 0),
('CP0043', 'Iron Steam',                                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2750.00,  20.00,  3300.00, true, 0),
('CP0044', 'Iron Dry',                                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2750.00,  20.00,  3300.00, true, 0),
('CP0045', 'Cloth Rack (Normal)',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1900.00,  20.00,  2280.00, true, 0),
('CP0046', 'Rice Cooker 1.0 (Better One)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 4550.00,  20.00,  5460.00, true, 0),
('CP0047', 'Rice Cooker 2.8 (Camy)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6500.00, -16.92,  5400.00, true, 0),
('CP0048', 'Rice Cooker 2.2 (Kawashi)',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5900.00,  20.00,  7080.00, true, 0),
('CP0049', 'Plate 9"',                                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  280.00,  20.00,   336.00, true, 0),
('CP0050', 'Cup & Saucer Set',                           'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1950.00,  20.00,  2340.00, true, 0),
('CP0051', 'CM Mug',                                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  120.00,  20.00,   144.00, true, 0),
('CP0052', 'Malamine Tray (Royal)',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  350.00,  20.00,   420.00, true, 0),
('CP0053', 'Glass 10/12 Set',                            'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  730.00,  15.00,   839.50, true, 0),
('CP0054', 'Filter (National)',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5900.00,  15.00,  6785.00, true, 0),
('CP0055', 'Gas Cooker Single (Black Ford)',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1950.00,  20.00,  2340.00, true, 0),
('CP0056', 'Gas Cooker Double (Black Ford)',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2850.00,  20.00,  3420.00, true, 0),
('CP0057', 'Gas Cooker Double Indian 2 Burner',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2850.00,  15.00,  3277.50, true, 0),
('CP0058', 'Gas Cooker Double Normal',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 3750.00,  15.00,  4312.50, true, 0),
('CP0059', 'Gas Cooker Legend',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6900.00,  20.00,  8280.00, true, 0),
('CP0060', 'Gas Cooker Double (Golden)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2850.00,  20.00,  3420.00, true, 0),
('CP0061', 'Gas Cooker Single',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1800.00,  25.38,  2256.75, true, 0),
('CP0062', 'Gas Cooker Double (Better One)',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2850.00,  20.00,  3420.00, true, 0),
('CP0063', 'My Home',                                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2250.00,  25.00,  2812.50, true, 0),
('CP0064', 'Ceramic Mug',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  285.00,  20.00,   342.00, true, 0),
('CP0065', 'Hot Mug',                                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  320.00,  20.00,   384.00, true, 0),
('CP0066', 'Kerosine Stove',                             'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 3250.00,  15.00,  3737.50, true, 0),
('CP0067', 'Flask 0.450ml (Wasuka)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1450.00,  20.00,  1740.00, true, 0),
('CP0068', 'Curry Dish (Ceramic)',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  280.00,  20.00,   336.00, true, 0),
('CP0069', 'Plate 10.5" (Ceramic)',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  380.00,  25.00,   475.00, true, 0),
('CP0070', 'Fry Pan 26"',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1900.00,  20.00,  2280.00, true, 0),
('CP0071', 'Fry Pan 24"',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1700.00,  25.00,  2125.00, true, 0),
('CP0072', 'Fry Pan 20"',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1530.00,  20.00,  1836.00, true, 0),
('CP0073', 'Fry Pan 22"',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1650.00,  20.00,  1980.00, true, 0),
('CP0074', 'Lunch Box (Silver)',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  250.00,  20.00,   300.00, true, 0),
('CP0075', 'Heater Jug 1.8 (Wasuka)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 1650.00,  20.00,  1980.00, true, 0),
('CP0076', 'Scale (Camy)',                               'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  650.00,  20.00,   780.00, true, 0),
('CP0077', 'Blender (Sonali) 5.9k',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5900.00,  20.00,  7080.00, true, 0),
('CP0078', 'Blender (Kawashi)',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 7750.00,  20.00,  9300.00, true, 0),
('CP0079', 'Blender (Optimus)',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 8750.00,  20.00, 10500.00, true, 0),
('CP0080', 'Iron Steam (Black Ford)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2250.00,  20.00,  2700.00, true, 0),
('CP0081', 'Oven 16Lit (Kawashi)',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2850.00,  30.00,  3705.00, true, 0),
('CP0082', 'Oven 25Lit (Kawashi)',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 3250.00,  30.00,  4225.00, true, 0),
('CP0083', 'Oven 38Lit (Kawashi)',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 4250.00,  30.00,  5525.00, true, 0),
('CP0084', 'Oven 48Lit (Kawashi)',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5250.00,  30.00,  6825.00, true, 0),
('CP0085', 'Stand Fan',                                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6185.00,  25.37,  7754.44, true, 0),
('CP0086', 'Wall Fan',                                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6900.00,  30.00,  8970.00, true, 0),
('CP0087', 'Cloth Rack Heavy No-2',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 4850.00,  20.00,  5820.00, true, 0),
('CP0088', 'Cloth Rack Heavy No-01',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5900.00,  20.00,  7080.00, true, 0),
('CP0089', 'Rotty Pan (L)',                              'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 2950.00,  20.00,  3540.00, true, 0),
('CP0090', 'New King Bowl',                              'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   25.00,  20.00,    30.00, true, 0),
('CP0091', 'Fry Pan 18"',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  890.00,  20.00,  1068.00, true, 0),
('CP0092', 'Phonnix Bucket 60LIT',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  600.00,  15.00,   690.00, true, 0),
('CP0093', 'Flower Tray New (S)',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   28.00,  30.00,    36.40, true, 0),
('CP0094', 'Tea Cup New',                                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   25.00,  30.00,    32.50, true, 0),
('CP0095', 'Finger Bowl (New)',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   50.00,  30.00,    65.00, true, 0),
('CP0096', 'Measuring Cup (New)',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  300.00,  30.00,   390.00, true, 0),
('CP0097', 'Measuring Cup With Lid',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  150.00,  30.00,   195.00, true, 0),
('CP0098', 'Tumbler',                                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   15.00,  30.00,    19.50, true, 0),
('CP0099', 'Plastic Broom 777',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   90.00,  20.00,   108.00, true, 0),
('CP0100', 'Plastic Broom 777 NO-02',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   70.00,  20.00,    84.00, true, 0),
('CP0101', 'Plastic Broom 999',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   90.00,  30.00,   117.00, true, 0),
('CP0102', 'Plastic Broom 2525',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  115.00,  20.00,   138.00, true, 0),
('CP0103', 'Plastic Broom 3025',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  230.00,  20.00,   276.00, true, 0),
('CP0104', 'Round Toilet Brush',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  100.00,  20.00,   120.00, true, 0),
('CP0105', 'Angle Toilet Brush',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   95.00,  20.00,   114.00, true, 0),
('CP0106', 'Angle Dek Brush',                            'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  140.00,  20.00,   168.00, true, 0),
('CP0107', 'Cloth Brush 2040',                           'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   50.00,  20.00,    60.00, true, 0),
('CP0108', 'Mob Small',                                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  120.00,   0.00,   120.00, true, 0),
('CP0109', 'Mob Large',                                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  140.00,   0.00,   140.00, true, 0),
('CP0110', 'Jenaretor 1200W',                            'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),10500.00,  30.00, 13650.00, true, 0),
('CP0111', 'Sulagu',                                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  290.00,  10.00,   319.00, true, 0),
('CP0112', 'Broom (2030)',                               'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  160.00,  10.00,   176.00, true, 0),
('CP0113', 'Dustpan With Brush',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  120.00,  10.00,   132.00, true, 0),
('CP0114', 'Grinder (Sonali)',                           'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6900.00,  15.00,  7935.00, true, 0),
('CP0115', 'Sulagu Round',                               'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),   90.00,  15.00,   103.50, true, 0),
('CP0116', 'Wire Mop',                                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  150.00,  20.00,   180.00, true, 0),
('CP0117', 'Table Fan',                                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5750.00,  25.00,  7187.50, true, 0),
('CP0118', 'Rice Cooker 2.2L (Nippon)',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6100.00,   0.00,  6100.00, true, 0),
('CP0119', 'Rice Cooker 1.8L (Nippon)',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 5800.00,   0.00,  5800.00, true, 0),
('CP0120', 'Glasstop Gas Cooker Double (Nippon)',        'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6100.00,   0.00,  6100.00, true, 0),
('CP0121', 'Glasstop Gas Cooker Double (Better One)',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6100.00,   0.00,  6100.00, true, 0),
('CP0122', 'Rice Cooker 2.8L (Sumsonic)',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'), 6100.00,   0.00,  6100.00, true, 0),
('CP0123', 'Only Cup 220CC',                             'PLASTIC', (SELECT id FROM inv_brands WHERE name='COLOMBO PRODUCTS'),  900.00,  22.22,  1100.00, true, 0),

-- ---- R.N.I INDUSTRIES (RNI0001–RNI0119) ----
('RNI0001', 'Aluminium Cake Tray (S)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  500.00,  25.00,   625.00, true, 0),
('RNI0002', 'Aluminium Cake Tray (M)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  560.00,  25.00,   700.00, true, 0),
('RNI0003', 'Aluminium Cake Tray (L)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  825.00,  25.00,  1031.25, true, 0),
('RNI0004', 'Aluminium Pot 20"',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  930.00,  30.00,  1209.00, true, 0),
('RNI0005', 'Aluminium Pot 18"',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  590.00,  30.00,   767.00, true, 0),
('RNI0006', 'Aluminium Pittu Bambu (Handle)',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  650.00,  30.00,   845.00, true, 0),
('RNI0007', 'Aluminium Pittu Bambu (L)',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  515.00,  30.00,   669.50, true, 0),
('RNI0008', 'Dust Bin (Round) PLT',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  514.00, -12.69,   448.75, true, 0),
('RNI0009', 'Polish Thachi No-08',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  480.00,  25.00,   600.00, true, 0),
('RNI0010', 'Polish Thachi No-12',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  795.00,  25.00,   993.75, true, 0),
('RNI0011', 'Polish Thachi No-11',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  695.00,  15.00,   799.25, true, 0),
('RNI0012', 'Polish Athili No-12',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  540.00,  15.00,   621.00, true, 0),
('RNI0013', 'Polish Athili No-15 (A)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),   21.00,   0.00,    21.00, true, 0),
('RNI0014', 'Polish Athili No-15 (B)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  760.00,  15.00,   874.00, true, 0),
('RNI0015', 'Polish Athili No-13',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  590.00,  15.00,   678.50, true, 0),
('RNI0016', 'Polish Athili No-14',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  790.00,  15.00,   908.50, true, 0),
('RNI0017', 'Polish Athili No-5',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  205.00,  15.00,   235.75, true, 0),
('RNI0018', 'Polish Athili No-11',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  490.00,  15.00,   563.50, true, 0),
('RNI0019', 'Polish Athili No-10',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  465.00,  15.00,   534.75, true, 0),
('RNI0020', 'Polish Athili No-6',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  525.00,  15.00,   603.75, true, 0),
('RNI0021', 'Polish Athili No-8',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  315.00,  15.00,   362.25, true, 0),
('RNI0022', 'Polish Athili No-7',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  285.00,  15.00,   327.75, true, 0),
('RNI0023', 'Kidaram Moodi No-15-1/2',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  925.00,  15.00,  1063.75, true, 0),
('RNI0024', 'Kidaram Moodi No-16-1/2',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1235.00,  15.00,  1420.25, true, 0),
('RNI0025', 'Kidaram Moodi No-18-1/2',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1100.00,  15.00,  1265.00, true, 0),
('RNI0026', 'Aluminium Rotty Pan (L)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  365.00,  15.00,   419.75, true, 0),
('RNI0027', 'Aluminium Rotty Pan (M)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  225.00,  10.00,   247.50, true, 0),
('RNI0028', 'Curry Moodi No-01',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  225.00,  25.00,   281.25, true, 0),
('RNI0029', 'Curry Moodi No-03',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  275.00,  30.00,   357.50, true, 0),
('RNI0030', 'Curry Moodi No-05',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  340.00,  30.00,   442.00, true, 0),
('RNI0031', 'Curry Moodi No-06',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  380.00,  30.00,   494.00, true, 0),
('RNI0032', 'Curry Moodi No-07',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  475.00,  20.00,   570.00, true, 0),
('RNI0033', 'Hopper Lid No-02',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  180.00,  20.00,   216.00, true, 0),
('RNI0034', 'Hopper Lid No-03',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  195.00,  20.00,   234.00, true, 0),
('RNI0035', 'Hopper Lid No-04',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  200.00,  25.00,   250.00, true, 0),
('RNI0036', 'Hopper Lid No-01',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  180.00,  15.00,   207.00, true, 0),
('RNI0037', 'Thachi 2D',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 2700.00,  20.00,  3240.00, true, 0),
('RNI0038', 'Thachi 4D-P',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1600.00,  15.00,  1840.00, true, 0),
('RNI0039', 'Thachi 1D',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 3800.00,  10.00,  4180.00, true, 0),
('RNI0040', 'Thachi No-02',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  180.00,  20.00,   216.00, true, 0),
('RNI0041', 'Steeme (L) Set',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1200.00,  25.00,  1500.00, true, 0),
('RNI0042', 'Idly Pot (L) Set',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1600.00,  25.00,  2000.00, true, 0),
('RNI0043', 'Nembili 000 Set',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  375.00,  20.00,   450.00, true, 0),
('RNI0044', 'Nembiliya 00',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  390.00,  25.00,   487.50, true, 0),
('RNI0045', 'Nembiliya 0',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  390.00,  20.00,   468.00, true, 0),
('RNI0046', 'Nembiliya 1',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  380.00,  25.00,   475.00, true, 0),
('RNI0047', 'Nembiliya 2',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  320.00,  20.00,   384.00, true, 0),
('RNI0048', 'Nembiliya 3',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  310.00,  15.00,   356.50, true, 0),
('RNI0049', 'Ethili No-7',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  365.00,  10.00,   401.50, true, 0),
('RNI0050', 'Ethili No-8',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  380.00,  15.00,   437.00, true, 0),
('RNI0051', 'Ethili No-9',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  475.00,  10.00,   522.50, true, 0),
('RNI0052', 'Ethili No-12',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  580.00,  20.00,   696.00, true, 0),
('RNI0053', 'Polish Thachi No-10',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  635.00,  15.00,   730.25, true, 0),
('RNI0054', 'Ethili No-16',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  860.00,  20.00,  1032.00, true, 0),
('RNI0055', 'Ethili No-20',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  800.00,  20.00,   960.00, true, 0),
('RNI0056', 'Ethili No-14',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  770.00,  15.00,   885.50, true, 0),
('RNI0057', 'Ethili No-11',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  510.00,  15.00,   586.50, true, 0),
('RNI0058', 'Ethili No-6',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  270.00,  20.00,   324.00, true, 0),
('RNI0059', 'Thachi No.1',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  190.00,  25.00,   237.50, true, 0),
('RNI0060', 'Thachi No.3',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  210.00,  30.00,   273.00, true, 0),
('RNI0061', 'Thachi No.4',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  260.00,  25.00,   325.00, true, 0),
('RNI0062', 'Thachi No-5',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  290.00,  20.00,   348.00, true, 0),
('RNI0063', 'Thachi No-6',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  380.00,  20.00,   456.00, true, 0),
('RNI0064', 'Thachi No-7',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  420.00,  20.00,   504.00, true, 0),
('RNI0065', 'Thachi No.9',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  570.00,  20.00,   684.00, true, 0),
('RNI0066', 'Ethili -00',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1100.00,  20.00,  1320.00, true, 0),
('RNI0067', 'Athili -0',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  360.00,  20.00,   432.00, true, 0),
('RNI0068', 'Athili -000',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  420.00,  15.00,   483.00, true, 0),
('RNI0069', 'Hopper Pan (L)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  550.00,  25.00,   687.50, true, 0),
('RNI0070', 'Hopper Pan (M)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  380.00,  30.00,   494.00, true, 0),
('RNI0071', 'Mob Basket',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  775.00,  25.00,   968.75, true, 0),
('RNI0072', 'Kidaram 0',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 9000.00,  12.50, 10125.00, true, 0),
('RNI0073', 'AP Thachi',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 3950.00,  20.00,  4740.00, true, 0),
('RNI0074', 'Rawana Set (L)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1350.00,  20.00,  1620.00, true, 0),
('RNI0075', 'Rawana Set (M)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1100.00,  15.00,  1265.00, true, 0),
('RNI0076', 'Rawana Set (L) New',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1530.00,  20.00,  1836.00, true, 0),
('RNI0077', 'Aluminium Kettle NO-10',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1100.00,  30.00,  1430.00, true, 0),
('RNI0078', 'Curry Moodi - 02',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  250.00,  15.00,   287.50, true, 0),
('RNI0079', 'Polish Athili Num-09',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  430.00,  10.00,   473.00, true, 0),
('RNI0080', 'Polish Athili Num-16',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1050.00,  10.00,  1155.00, true, 0),
('RNI0081', 'Polish Thachi No-01',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  220.00,  10.00,   242.00, true, 0),
('RNI0082', 'Polish Thachi No-02',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  220.00,  10.00,   242.00, true, 0),
('RNI0083', 'Polish Thachi No-03',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  240.00,  10.00,   264.00, true, 0),
('RNI0084', 'Polish Thachi No-04',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  285.00,  10.00,   313.50, true, 0),
('RNI0085', 'Polish Thachi No-05',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  310.00,  10.00,   341.00, true, 0),
('RNI0086', 'Polish Thachi No-06',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  420.00,  10.00,   462.00, true, 0),
('RNI0087', 'Polish Thachi No-07',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  450.00,  10.00,   495.00, true, 0),
('RNI0088', 'Polish Thachi No-09',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  610.00,  10.00,   671.00, true, 0),
('RNI0089', 'Handle Thachi No-08',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  750.00,  10.00,   825.00, true, 0),
('RNI0090', 'Handle Thachi No-06',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  650.00,  10.00,   715.00, true, 0),
('RNI0091', 'Meti Coppa No-03',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  170.00,  30.00,   221.00, true, 0),
('RNI0092', 'Meti Coppa No-04',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  200.00,  30.00,   260.00, true, 0),
('RNI0093', 'Meti Coppa No-05',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  210.00,  30.00,   273.00, true, 0),
('RNI0094', 'Meti Coppa No-06',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  240.00,  30.00,   312.00, true, 0),
('RNI0095', 'Meti Coppa No-07',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  315.00,  30.00,   409.50, true, 0),
('RNI0096', 'Meti Coppa No-08',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  350.00,  30.00,   455.00, true, 0),
('RNI0097', 'Meti Coppa No-04 (Alt)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  200.00,  30.00,   260.00, true, 0),
('RNI0098', 'Idly Pot - Medium',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1300.00,  30.00,  1690.00, true, 0),
('RNI0099', 'Steamer (S)',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  950.00,  30.00,  1235.00, true, 0),
('RNI0100', 'Steamer (M)',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  150.00,  30.00,   195.00, true, 0),
('RNI0101', 'Steamer (Xxl)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1400.00,  30.00,  1820.00, true, 0),
('RNI0102', 'Steamer (Xxxl)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1600.00,  30.00,  2080.00, true, 0),
('RNI0103', 'Rotti Thachi (Ch) - Small',        'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  830.00,  15.00,   954.50, true, 0),
('RNI0104', 'Rotti Thachi (Ch) Medium',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1400.00,  15.00,  1610.00, true, 0),
('RNI0105', 'Rotti Thachi (Ch) Xl',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1916.00,  15.00,  2203.40, true, 0),
('RNI0106', 'Aluminium Kettle No-14',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1350.00,  15.00,  1552.50, true, 0),
('RNI0107', 'Aluminium Kettle No-08',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  950.00,  10.00,  1045.00, true, 0),
('RNI0108', 'Aluminium Kettle No-10',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 1250.00,  10.00,  1375.00, true, 0),
('RNI0109', 'Table Cloth Transparent (15mm)',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),15000.00,  10.00, 16500.00, true, 0),
('RNI0110', 'Un Polish Ethili No-07',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  300.00,  10.00,   330.00, true, 0),
('RNI0111', 'Un Polish Ethili No-08',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  325.00,  10.00,   357.50, true, 0),
('RNI0112', 'Un Polish Ethili No-12',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  660.00,  10.00,   726.00, true, 0),
('RNI0113', 'Un Polish Ethili No-13',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  760.00,  10.00,   836.00, true, 0),
('RNI0114', 'Infrared Cooker',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'), 3750.00,  10.00,  4125.00, true, 0),
('RNI0115', 'Rice Bowl (S)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  650.00,  30.00,   845.00, true, 0),
('RNI0116', 'Rice Bowl (L)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  850.00,  10.00,   935.00, true, 0),
('RNI0117', 'Cake Tray Round (S)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  350.00,  10.00,   385.00, true, 0),
('RNI0118', 'Cake Tray Round (M)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  550.00,  10.00,   605.00, true, 0),
('RNI0119', 'Cake Tray Round (L)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='R.N.I INDUSTRIES'),  750.00,  10.00,   825.00, true, 0),

-- ---- Uniplast Lanka (PVT) LTD (UNI0001–UNI0007) ----
('UNI0001', 'Drawer 6Pcs (Uni)',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='Uniplast Lanka (PVT) LTD'),  7391.00,   0.00,  7391.00, true, 0),
('UNI0002', 'Echo Flex 5D Mix',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='Uniplast Lanka (PVT) LTD'),  8847.00,   0.00,  8847.00, true, 0),
('UNI0003', 'Drawer 5-2 (Uni)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='Uniplast Lanka (PVT) LTD'), 12150.00,  25.00, 15187.50, true, 0),
('UNI0004', 'Heater Jug',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='Uniplast Lanka (PVT) LTD'),  1300.00,  25.00,  1625.00, true, 0),
('UNI0005', 'Jawa 6D Mix',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='Uniplast Lanka (PVT) LTD'),  7391.00,   0.00,  7391.00, true, 0),
('UNI0006', 'Piccolo 6 Drawer Mix',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='Uniplast Lanka (PVT) LTD'),  4710.00,   0.00,  4710.00, true, 0),
('UNI0007', 'Echo Lokka 5-2 L Full Cover Mix','PLASTIC', (SELECT id FROM inv_brands WHERE name='Uniplast Lanka (PVT) LTD'), 12960.00,   0.00, 12960.00, true, 0);

-- ============================================================
-- Source: ghanim-wholesale V6 (plastic items part 2)
-- V6 — Plastic items part 2 (remaining suppliers)
-- Continues from V5: JM, MAV, NPI, RB, RTDK, SNS, OWN
-- item_code format: {SUPPLIER_CODE}{4-digit sequential}

INSERT INTO inv_items (item_code, description, category, brand_id, mrp, margin_pct, wholesale_price, active, stock_qty) VALUES

-- ---- JAYAMALI PLASTIC (JM0001–JM0020) ----
('JM0001', 'Jayamali Hanging Pot',      'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  130.00,  20.00,  156.00, true, 0),
('JM0002', 'Jayamali Round Pot',        'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  106.00,   1.89,  108.00, true, 0),
('JM0003', 'Jayamali Sq Pot',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  106.00,   4.15,  110.40, true, 0),
('JM0004', 'Jayamali 35CM Pot (L)',     'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  200.00,  20.00,  240.00, true, 0),
('JM0005', 'Jayamali 35CM Pot (S)',     'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   81.00,  20.00,   97.20, true, 0),
('JM0006', 'Jayamali Mini Pot',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   43.00,  20.00,   51.60, true, 0),
('JM0007', 'Bucket Without Lid 7LTR',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  102.00,  20.00,  122.40, true, 0),
('JM0008', 'Rose Basin',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  132.00,  20.00,  158.40, true, 0),
('JM0009', '1250ML Beaker (JM)',        'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   35.00,  20.00,   42.00, true, 0),
('JM0010', '750ML Beaker (JM)',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   24.00,  20.00,   28.80, true, 0),
('JM0011', 'Spring Hopper Set',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   60.00,  25.00,   75.00, true, 0),
('JM0012', 'Baby Rose Basin',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  114.00,  25.00,  142.50, true, 0),
('JM0013', 'String Hopper Tray',        'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   60.00,  20.00,   72.00, true, 0),
('JM0014', 'Dessert Cup 6x1',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  285.00, -57.89,  120.00, true, 0),
('JM0015', 'Crow Pot',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   22.50,  25.00,   28.13, true, 0),
('JM0016', '12 Basin',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   63.00,  25.00,   78.75, true, 0),
('JM0017', 'Basin 519',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),  220.00,  20.00,  264.00, true, 0),
('JM0018', 'Madaku (Jayamali)',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   20.00,  20.00,   24.00, true, 0),
('JM0019', 'Beaker (VR)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   55.00,  30.00,   71.50, true, 0),
('JM0020', 'Basin 216',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='JAYAMALI PLASTIC'),   52.00,  25.00,   65.00, true, 0),

-- ---- MAV PLASTIC (MAV0001–MAV0012) ----
('MAV0001', '20LTR Bucket (Mav)',       'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  350.00,  20.00,  420.00, true, 0),
('MAV0002', '22LTR Bucket (Mav)',       'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),    0.00,  20.00,    0.00, true, 0),
('MAV0003', '522 Basin (Mav)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  450.00,  20.00,  540.00, true, 0),
('MAV0004', '565 Basin (Mav)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  825.00,  20.00,  990.00, true, 0),
('MAV0005', '520 Basin',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  325.00,  20.00,  390.00, true, 0),
('MAV0006', 'Basin 920',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  400.00,  25.00,  500.00, true, 0),
('MAV0007', 'Basin 522',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  450.00,  25.00,  562.50, true, 0),
('MAV0008', 'Basin 16"',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  119.00,  25.00,  148.75, true, 0),
('MAV0009', 'Food Cover Round',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  285.00,  20.00,  342.00, true, 0),
('MAV0010', 'Buvket',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  100.00,  20.00,  120.00, true, 0),
('MAV0011', 'Aluminium Kettle No-03',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  640.00,  10.00,  704.00, true, 0),
('MAV0012', 'Aluminium Kettle No-02',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='MAV PLASTIC'),  340.00,  10.00,  374.00, true, 0),

-- ---- NIPPON PLASTIC INDUSTRIES (NPI0001–NPI0141) ----
('NPI0001', 'Water Bottle Relly (L)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  260.00,  25.00,   325.00, true, 0),
('NPI0002', 'Baby Rack Eco',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  821.00, -15.96,   690.00, true, 0),
('NPI0003', 'Water Jug (L)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  342.00, -28.07,   246.00, true, 0),
('NPI0004', 'Watering Can 07 Ltr',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  588.00,  20.00,   705.60, true, 0),
('NPI0005', 'Drawer Set (Mini 05PCS)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  924.00, -15.58,   780.00, true, 0),
('NPI0006', 'Spray Bottle 475ML',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   25.00,  20.00,    30.00, true, 0),
('NPI0007', 'Square Pot (No 4)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  171.00, -15.79,   144.00, true, 0),
('NPI0008', 'Square Pot (No 5)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  257.00, -15.95,   216.00, true, 0),
('NPI0009', 'Floral Pot (Xl)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  165.00,  20.00,   198.00, true, 0),
('NPI0010', 'Square Pot (No 2)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   43.00,  20.00,    51.60, true, 0),
('NPI0011', 'Rio Baby Basket',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1142.00, -15.94,   960.00, true, 0),
('NPI0012', 'Ubag (S)',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   85.00,  20.00,   102.00, true, 0),
('NPI0013', '50LTR Bucket',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  893.00, -16.01,   750.00, true, 0),
('NPI0014', '25LTR Bucket Eco',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  571.00, -15.94,   480.00, true, 0),
('NPI0015', 'Plate Rack (L) Eco',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1207.00, -16.09,  1012.80, true, 0),
('NPI0016', 'Kitchen Spice Set',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  840.00,  20.00,  1008.00, true, 0),
('NPI0017', 'Printed Plate (Nippon)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   21.00,  82.86,    38.40, true, 0),
('NPI0018', 'Tray Flora',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  120.00,  20.00,   144.00, true, 0),
('NPI0019', 'Waste Bin Polo (N)',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  115.00,  20.00,   138.00, true, 0),
('NPI0020', 'Dustbin With Lid (M) Vr',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  135.00, -15.51,   114.06, true, 0),
('NPI0021', '3LTR Bucket (Virgin)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  130.00,  20.00,   156.00, true, 0),
('NPI0022', '22" Basin',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  430.00,  20.00,   516.00, true, 0),
('NPI0023', '22" Basin (N)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  430.00,  20.00,   516.00, true, 0),
('NPI0024', 'Milk Strainer (S) Nippon',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   52.00, -19.23,    42.00, true, 0),
('NPI0025', 'Milk Strainer (L) Nippon',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   60.00,  20.00,    72.00, true, 0),
('NPI0026', 'Soap Case 1002',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   62.00, -16.77,    51.60, true, 0),
('NPI0027', 'Measuring Cup (N)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   32.00,  20.00,    38.40, true, 0),
('NPI0028', 'Beer Cup (N)',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   25.00,  20.00,    30.00, true, 0),
('NPI0029', 'Mini Baby Rack (Nippon)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  800.00, -16.00,   672.00, true, 0),
('NPI0030', 'Basin 18"',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  394.00, -12.75,   343.75, true, 0),
('NPI0031', 'Basin 20',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  464.00,  -0.05,   463.75, true, 0),
('NPI0032', 'Basin 22',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  614.00, -12.50,   537.25, true, 0),
('NPI0033', 'Basin 23',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  786.00, -12.53,   687.50, true, 0),
('NPI0034', 'Basin 36 Ltr PLT',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  450.00, -12.50,   393.75, true, 0),
('NPI0035', 'Basin 40LTR Tub PLT',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  600.00, -12.50,   525.00, true, 0),
('NPI0036', 'Basin 50LTR Tub PLT',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  850.00, -12.50,   743.75, true, 0),
('NPI0037', 'New Baby Basin',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  261.00, -12.84,   227.50, true, 0),
('NPI0038', 'Water Jug (S)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  193.00, -12.56,   168.75, true, 0),
('NPI0039', 'Food Cover Normal',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  529.00, -12.57,   462.50, true, 0),
('NPI0040', 'Monica Container - Eco',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  164.00,   2.90,   168.75, true, 0),
('NPI0041', 'Egg Tray',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  121.00, -13.22,   105.00, true, 0),
('NPI0042', 'Sink Watty (S)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   81.00, -13.58,    70.00, true, 0),
('NPI0043', 'Sink Watty (M)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  100.00, -12.50,    87.50, true, 0),
('NPI0044', 'Sink Watty (L)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  114.00, -13.38,    98.75, true, 0),
('NPI0045', 'Round Till',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   77.00, -13.96,    66.25, true, 0),
('NPI0046', 'U Bag (S)',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  118.00, -13.14,   102.50, true, 0),
('NPI0047', 'Drawer Set 5PCS (NIP)',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 3250.00,  25.00,  4062.50, true, 0),
('NPI0048', 'Kitchen Rack (L) W/O Cartoon',     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1642.00, -12.50,  1436.75, true, 0),
('NPI0049', 'Bucket 50LIT Green',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  857.00, -12.49,   750.00, true, 0),
('NPI0050', 'Bucket 25LIT (Black)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  557.00, -12.50,   487.38, true, 0),
('NPI0051', 'Bucket 9 Lit',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  250.00, -12.50,   218.75, true, 0),
('NPI0052', 'Bucket 11 Lit',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  321.00, -12.77,   280.00, true, 0),
('NPI0053', 'Bucket 13 Lit',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  393.00, -12.53,   343.75, true, 0),
('NPI0054', 'Bucket 15LIT',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  421.00, -12.71,   367.50, true, 0),
('NPI0055', '6 Lit Bucket Without Lid',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  171.00, -13.01,   148.75, true, 0),
('NPI0056', 'Dust Bin (S)',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  100.00,  75.00,   175.00, true, 0),
('NPI0057', 'Dust Bin New',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  168.00, -12.95,   146.25, true, 0),
('NPI0058', 'Dustbin Round',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  149.00, -12.75,   130.00, true, 0),
('NPI0059', 'Laundry Basket (M) PLT With Lid',  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  643.00, -12.52,   562.50, true, 0),
('NPI0060', 'Laundry Basket Super Large - PLT', 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  757.00, -12.65,   661.25, true, 0),
('NPI0061', 'Baby Commode',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  316.00, -12.58,   276.25, true, 0),
('NPI0062', 'Finger Bowl (L)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   86.00, -12.79,    75.00, true, 0),
('NPI0063', 'Finger Bowl (S)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   34.00, -15.44,    28.75, true, 0),
('NPI0064', 'Vegetable Basket (L) - Eco',       'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  321.00, -12.77,   280.00, true, 0),
('NPI0065', 'Floral Pot NO-01',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  171.00, -13.01,   148.75, true, 0),
('NPI0066', 'Floral Pot NO-02',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  114.00, -13.38,    98.75, true, 0),
('NPI0067', 'Floral Pot NO-03',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   91.00, -13.46,    78.75, true, 0),
('NPI0068', 'Floral Pot - Mini',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   57.00, -14.47,    48.75, true, 0),
('NPI0069', 'Square Pot NO-03',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   94.00, -13.56,    81.25, true, 0),
('NPI0070', 'Square Pot NO-01',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   29.00, -13.79,    25.00, true, 0),
('NPI0071', 'Shoe Rack Forcus',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1250.00, -12.50,  1093.75, true, 0),
('NPI0072', 'Laundry Basket (L) Eco',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  985.00, -12.56,   861.25, true, 0),
('NPI0073', 'Laundry Basket Stylish',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1061.00, -12.58,   927.50, true, 0),
('NPI0074', 'Baby Rack (L)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1214.00, -12.58,  1061.25, true, 0),
('NPI0075', 'Basin Classic',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  300.00,  20.00,   360.00, true, 0),
('NPI0076', 'Spray Can',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  159.00, -20.75,   126.00, true, 0),
('NPI0077', 'New Soap Case',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   74.00, -17.30,    61.20, true, 0),
('NPI0078', 'Funnel (N) (S)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   45.00,  -4.00,    43.20, true, 0),
('NPI0079', 'Funnel (N) (M)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   68.00,  -2.94,    66.00, true, 0),
('NPI0080', 'Vegetable Basket SQ (S)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   55.00,  20.00,    66.00, true, 0),
('NPI0081', 'Baby Bath (L)',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1072.00, -16.04,   900.00, true, 0),
('NPI0082', 'Baby Bath',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  750.00,  20.00,   900.00, true, 0),
('NPI0083', '25" Basin',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  900.00, -16.00,   756.00, true, 0),
('NPI0084', 'Dustbin SQ - PLT',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  514.00, -12.69,   448.75, true, 0),
('NPI0085', 'Food Container (Eco)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  130.00,  25.00,   162.50, true, 0),
('NPI0086', 'Water Bottle - 203',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  238.00,  -9.87,   214.50, true, 0),
('NPI0087', 'Dustbin With Lid (M)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  419.00,  -7.78,   386.40, true, 0),
('NPI0088', 'Basin Fruit (M)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   92.00,  20.00,   110.40, true, 0),
('NPI0089', 'Basin Fruit (L)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  147.00,  20.00,   176.40, true, 0),
('NPI0090', 'Ice Cup With Spoon',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   17.00,  25.00,    21.25, true, 0),
('NPI0091', 'Dust Pan (NIP)',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   95.00,  20.00,   114.00, true, 0),
('NPI0092', 'Basin Fruit (S)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   77.00,  20.00,    92.40, true, 0),
('NPI0093', 'Basin Fruit (Xl)',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  166.00,  20.00,   199.20, true, 0),
('NPI0094', 'New Flower Basket (RB)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   40.00,  15.00,    46.00, true, 0),
('NPI0095', 'Bucket 100LIT',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1775.00,  25.00,  2218.75, true, 0),
('NPI0096', 'Basin Fruit (M) Alt',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   92.00,  25.00,   115.00, true, 0),
('NPI0097', 'Basin Fruit (L) Alt',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  147.00,  20.00,   176.40, true, 0),
('NPI0098', 'Feeding Cup',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   73.00, -14.52,    62.40, true, 0),
('NPI0099', 'Bucket 5LIT (NIP)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  150.00, -16.00,   126.00, true, 0),
('NPI0100', 'Bucket 3LIT (NIP)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   86.00,  20.00,   103.20, true, 0),
('NPI0101', 'Dust Bin Classic',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  386.00, -16.06,   324.00, true, 0),
('NPI0102', 'Rabbit Till',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   78.00, -15.38,    66.00, true, 0),
('NPI0103', 'Bucket 10 Ltr W/O Lid',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  271.00, -16.31,   226.80, true, 0),
('NPI0104', 'Water Bottle Ice Baby',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  215.00, -16.28,   180.00, true, 0),
('NPI0105', 'Mug (NIP)',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  108.00, -16.67,    90.00, true, 0),
('NPI0106', 'Shoe Rack (Old)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1036.00,  20.00,  1243.20, true, 0),
('NPI0107', 'New Flower Tray',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   55.00,  20.00,    66.00, true, 0),
('NPI0108', 'Soap Case 2 In 1',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   60.00,  20.00,    72.00, true, 0),
('NPI0109', 'Laundry Basket Super',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  757.00, -15.98,   636.00, true, 0),
('NPI0110', 'Lunch Box Looney',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  124.00,  20.00,   148.80, true, 0),
('NPI0111', 'Lunch Box Roy',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  135.00,  20.00,   162.00, true, 0),
('NPI0112', 'Lunch Box Latest',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  140.00,  20.00,   168.00, true, 0),
('NPI0113', 'Lunch Box Ideal',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  185.00,  20.00,   222.00, true, 0),
('NPI0114', 'Water Bottle 1000ML',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  259.00,  25.00,   323.75, true, 0),
('NPI0115', 'Water Bottle 1500ML',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  325.00,  25.00,   406.25, true, 0),
('NPI0116', '2 In 1 Soap Case',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   86.00, -12.79,    75.00, true, 0),
('NPI0117', 'Laundry Oval',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  190.00,  20.00,   228.00, true, 0),
('NPI0118', 'Tray New',                          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  557.00, -55.89,   245.70, true, 0),
('NPI0119', 'Funnel (L)',                         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  121.00, -12.19,   106.25, true, 0),
('NPI0120', 'Multi Purpose Box (S)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  285.00,  20.00,   342.00, true, 0),
('NPI0121', 'Multi Purpose Box (M)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  645.00,  20.00,   774.00, true, 0),
('NPI0122', 'Multi Purpose Box (L)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  746.00,  20.00,   895.20, true, 0),
('NPI0123', 'Pencil Box Double Door',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  160.00,  25.00,   200.00, true, 0),
('NPI0124', 'Pencil Box Young Master',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   54.60,  30.00,    70.98, true, 0),
('NPI0125', 'Fruit Bowl NO-14',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   90.00,  25.00,   112.50, true, 0),
('NPI0126', 'Basin 30Lit',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  514.00,  -9.20,   466.70, true, 0),
('NPI0127', 'Basin No-40 New PLT',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  153.00,  -9.09,   139.10, true, 0),
('NPI0128', 'Bucket 50Lit Black',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  614.00,  30.00,   798.20, true, 0),
('NPI0129', 'Pedal Dust Bin',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  895.00,  30.00,  1163.50, true, 0),
('NPI0130', 'Food Cover No-01',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  680.00,  30.00,   884.00, true, 0),
('NPI0131', 'Kitchen Rack (S)',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 1006.60,  30.00,  1308.58, true, 0),
('NPI0132', 'Kitchen Rack (L)',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  845.00,  30.00,  1098.50, true, 0),
('NPI0133', 'Drawer 4PCS (NIP)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 4143.00,  10.00,  4557.30, true, 0),
('NPI0134', 'Drawer 3PCS (NIP)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'), 3714.00,  10.00,  4085.40, true, 0),
('NPI0135', 'Vegetable Basket (L)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  321.00,   0.00,   321.00, true, 0),
('NPI0136', 'Pencil Box (Marvel)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  130.00,  25.00,   162.50, true, 0),
('NPI0137', 'Pencil Box (Micky)',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   85.00,  20.00,   102.00, true, 0),
('NPI0138', 'Pencil Box (Double Door)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  185.00,  20.00,   222.00, true, 0),
('NPI0139', 'Pencil Box (Young Master)',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),   55.00,  20.00,    66.00, true, 0),
('NPI0140', 'Lunch Box Lucky',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  211.00,  20.00,   253.20, true, 0),
('NPI0141', 'Plastic Crate (M)',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='NIPPON PLASTIC INDUSTRIES'),  964.00,  -6.64,   900.00, true, 0),

-- ---- RAINBOW (RB0001–RB0109) ----
('RB0001', 'Flora Container 444',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   95.00,  15.00,  109.25, true, 0),
('RB0002', 'Flora Container 555',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  140.00,  15.00,  161.00, true, 0),
('RB0003', 'Flora Container 666',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  200.00,  15.00,  230.00, true, 0),
('RB0004', 'Flora Container 777',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  275.00,  -5.91,  258.75, true, 0),
('RB0005', 'Flora Container 888',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  365.00,  -7.05,  339.25, true, 0),
('RB0006', 'Flora Container 999',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  460.00,  -6.25,  431.25, true, 0),
('RB0007', 'Shoe Rack (Vittora)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  890.00,  10.00,  979.00, true, 0),
('RB0008', 'Book Rack (S) 5 Layer V/R',     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 1950.00,  15.00, 2242.50, true, 0),
('RB0009', 'Book Rack (S) 6 Layer V/R',     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 2350.00,  15.00, 2702.50, true, 0),
('RB0010', 'Book Rack (S) 7 Layer V/R',     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 2750.00,  15.00, 3162.50, true, 0),
('RB0011', 'Book Rack (L) 6 Layer V/R',     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 2750.00,  15.00, 3162.50, true, 0),
('RB0012', 'RB New Trey (L)',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  285.00,  15.00,  327.75, true, 0),
('RB0013', 'RB New Trey (S)',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  165.00,  15.00,  189.75, true, 0),
('RB0014', 'Water Spoon (V/R)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   65.00,  15.00,   74.75, true, 0),
('RB0015', 'Soap Basket PL',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   97.00,  15.00,  111.55, true, 0),
('RB0016', 'RB Food Container (M)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  115.00,  15.00,  132.25, true, 0),
('RB0017', 'Food Container (L)',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  185.00,  15.00,  212.75, true, 0),
('RB0018', 'Tea Cup',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   12.50,  15.00,   14.38, true, 0),
('RB0019', 'Lenovo Rack (White) A',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 1900.00,  15.00, 2185.00, true, 0),
('RB0020', 'Lenovo Rack (White) B',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 1900.00,  15.00, 2185.00, true, 0),
('RB0021', 'Funnel (Rainbow) L',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   35.00,  15.00,   40.25, true, 0),
('RB0022', 'Flora Container 111',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   35.00,  15.00,   40.25, true, 0),
('RB0023', 'Flora Container 222',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   52.50,  15.00,   60.38, true, 0),
('RB0024', 'Flora Container 333',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   77.50,  15.00,   89.13, true, 0),
('RB0025', 'Magic Fruit Basket (L)',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  107.50,  15.00,  123.63, true, 0),
('RB0026', 'Magic Fruit Basket (M)',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   62.50,  15.00,   71.88, true, 0),
('RB0027', 'Flower Basket NO-01',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   20.00,  15.00,   23.00, true, 0),
('RB0028', 'Flower Basket NO-02 PL',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   30.00,  15.00,   34.50, true, 0),
('RB0029', 'Fruit Basket',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   55.00,  15.00,   63.25, true, 0),
('RB0030', 'Tray NO-01 PL',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   42.00,  15.00,   48.30, true, 0),
('RB0031', 'Elephant Basin PL',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  310.00,  15.00,  356.50, true, 0),
('RB0032', 'Joyo Basin PL',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  250.00,  15.00,  287.50, true, 0),
('RB0033', 'Dot Basin No-01 V/R',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   65.00,  15.00,   74.75, true, 0),
('RB0034', 'Dot Basin No-02 V/R',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   95.00,  81.58,  172.50, true, 0),
('RB0035', 'Dot Basin No-03 V/R',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  120.00,  15.00,  138.00, true, 0),
('RB0036', 'Pedal Dustbin (3PCS)',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  590.00,  15.00,  678.50, true, 0),
('RB0037', 'Soap Basket PL Alt',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   97.50,  15.00,  112.13, true, 0),
('RB0038', 'Food Cover (L) PL',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  350.00,  15.00,  402.50, true, 0),
('RB0039', 'Coffee Cup',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   23.00, -98.87,    0.26, true, 0),
('RB0040', 'Plate (Rainbow)',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   33.00,  15.00,   37.95, true, 0),
('RB0041', 'Dustpan PL',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   40.00,  15.00,   46.00, true, 0),
('RB0042', 'Laundry Basket (L) PL W/Lid',   'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  985.00, -44.54,  546.25, true, 0),
('RB0043', '03 Pcs Container',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  400.00,  15.00,  460.00, true, 0),
('RB0044', '05 Pcs Container',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  900.00,  15.00, 1035.00, true, 0),
('RB0045', '09 Pcs Container',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 1850.00, -12.97, 1610.00, true, 0),
('RB0046', '07 Pcs Container',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 1400.00,  15.00, 1610.00, true, 0),
('RB0047', 'Baby Basket (Twenkle)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  370.00,  15.00,  425.50, true, 0),
('RB0048', 'Family Basket',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  750.00,  15.00,  862.50, true, 0),
('RB0049', 'New Soap Case 2 In 1',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   86.00, -26.45,   63.25, true, 0),
('RB0050', 'Sq Oppo Bag',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  500.00,  15.00,  575.00, true, 0),
('RB0051', 'Easy Bag',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  360.00,  15.00,  414.00, true, 0),
('RB0052', 'Tray NO-5 PLT',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  167.50,  15.00,  192.63, true, 0),
('RB0053', 'Tray NO-02 PL',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   60.00,  15.00,   69.00, true, 0),
('RB0054', 'Tray NO-03 PL',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   97.50,  15.00,  112.13, true, 0),
('RB0055', 'Tray NO-04 PL',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  130.00,  15.00,  149.50, true, 0),
('RB0056', 'Dolphin Rack PL',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  600.00,  15.00,  690.00, true, 0),
('RB0057', 'Jeko Box - 06L',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  420.00,  15.00,  483.00, true, 0),
('RB0058', 'Jeko Box M - 10L',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  580.00,  15.00,  667.00, true, 0),
('RB0059', 'Jeko Box - 16L',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  875.00,  15.00, 1006.25, true, 0),
('RB0060', 'Jeko Box - 20L',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 1100.00,  15.00, 1265.00, true, 0),
('RB0061', 'Jeko Box Set (04 Pcs)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 2875.00,  15.00, 3306.25, true, 0),
('RB0062', 'Mega Tray',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  580.00,  15.00,  667.00, true, 0),
('RB0063', 'Vegetable Basket (PL)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  150.00,  15.00,  172.50, true, 0),
('RB0064', 'Onion Basket (R) Pallet',        'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   90.00,  15.00,  103.50, true, 0),
('RB0065', 'Funnel With H/L (R)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  180.00,  25.00,  225.00, true, 0),
('RB0066', 'King Bowl (L) PLT',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   40.00,  15.00,   46.00, true, 0),
('RB0067', 'King Bowl (S) PLT',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   18.00,  15.00,   20.70, true, 0),
('RB0068', 'Flower Pot NO-05 (RB)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   90.00,  15.00,  103.50, true, 0),
('RB0069', 'Juscobin',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  400.00,  15.00,  460.00, true, 0),
('RB0070', 'Kohra NO-04',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  120.00,  15.00,  138.00, true, 0),
('RB0071', 'Kohra NO-03',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   75.00,  15.00,   86.25, true, 0),
('RB0072', 'Kohra NO-02',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   30.00,  15.00,   34.50, true, 0),
('RB0073', 'Kohra NO-01',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   25.00,  15.00,   28.75, true, 0),
('RB0074', 'Lion Tray PLT',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  160.00,  18.59,  189.75, true, 0),
('RB0075', 'Rainbow Mug',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   90.00,  15.00,  103.50, true, 0),
('RB0076', 'Fruit Stool',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  190.00,  15.00,  218.50, true, 0),
('RB0077', 'Water Spoon (PLT)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   35.00,  30.00,   45.50, true, 0),
('RB0078', 'Flower Stool',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  300.00,  20.00,  360.00, true, 0),
('RB0079', 'Rainbow Stool PL',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  490.00,  15.00,  563.50, true, 0),
('RB0080', 'Ethili NO-10',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  530.00,  10.00,  583.00, true, 0),
('RB0081', 'Book Rack 5PCS (L)',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 2450.00,  10.00, 2695.00, true, 0),
('RB0082', 'Baby Chair (RB)',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  650.00,  15.00,  747.50, true, 0),
('RB0083', 'Baby Stool',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  175.00,  15.00,  201.25, true, 0),
('RB0084', 'Oval Tray',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  190.00,  15.00,  218.50, true, 0),
('RB0085', 'Rabbit Basin (RB)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  340.00,  15.00,  391.00, true, 0),
('RB0086', 'Soap Holder (RB)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   90.00,  15.00,  103.50, true, 0),
('RB0087', 'Lenova Rack',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'), 1750.00,  20.00, 2100.00, true, 0),
('RB0088', 'Drawer Set Mini RB',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  280.00,  15.00,  322.00, true, 0),
('RB0089', 'Concrete Thachi',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  120.00,  20.00,  144.00, true, 0),
('RB0090', 'Lion Tray VR',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  210.00,  15.00,  241.50, true, 0),
('RB0091', 'Dot Basin NO-04 V/R',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  160.00,  15.00,  184.00, true, 0),
('RB0092', 'Dot Basin NO-05 V/R',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  200.00,  15.00,  230.00, true, 0),
('RB0093', 'New Fruit Basket (RB)',          'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   97.50,  15.00,  112.13, true, 0),
('RB0094', 'Oval Tray Alt',                  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  170.00,  15.00,  195.50, true, 0),
('RB0095', 'Tea Mug',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   28.00,  15.00,   32.20, true, 0),
('RB0096', 'Dustbin (R) PL',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  100.00,  72.50,  172.50, true, 0),
('RB0097', 'Dust Bin (S) PL',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   75.00,  15.00,   86.25, true, 0),
('RB0098', 'Lunch Box Smart',                'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  250.00,  15.00,  287.50, true, 0),
('RB0099', 'Lunch Box King',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  240.00,  15.00,  276.00, true, 0),
('RB0100', 'Lunch Box Bobo',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  250.00,  15.00,  287.50, true, 0),
('RB0101', 'Lunch Box Double Decker',        'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  275.00,  15.00,  316.25, true, 0),
('RB0102', 'Dustbin (S) V/R',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   95.00,  15.00,  109.25, true, 0),
('RB0103', 'Dustbin (R) V/R',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  135.00,  15.00,  155.25, true, 0),
('RB0104', 'Dustpan VR',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   70.00,  15.00,   80.50, true, 0),
('RB0105', 'King Bowl (L) VR',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   57.50,  15.00,   66.13, true, 0),
('RB0106', 'King Bowl (S) VR',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),   30.00,  15.00,   34.50, true, 0),
('RB0107', 'Stool Rainbow (V/R)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  865.00,   0.00,  865.00, true, 0),
('RB0108', 'Onion Basket (S) V/R W/Lid',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  140.00,   0.00,  140.00, true, 0),
('RB0109', 'Mu Rack V/R',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='RAINBOW'),  800.00,   0.00,  800.00, true, 0),

-- ---- RT&DK CONSUMERS(PVT)LTD (RTDK0001–RTDK0019) ----
('RTDK0001', 'NPN Mini Drawer (L) 03 Layers',  'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),   658.00,  20.00,   789.60, true, 0),
('RTDK0002', 'Iron Wangadi NO-08',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  3800.00,  20.00,  4560.00, true, 0),
('RTDK0003', 'Iron Wangadi NO-07',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  3200.00,  20.00,  3840.00, true, 0),
('RTDK0004', 'Basin Bongo',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),   296.00,  20.00,   355.20, true, 0),
('RTDK0005', 'Iron Wangadi NO-06',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  2650.00,  15.00,  3047.50, true, 0),
('RTDK0006', 'Iron Wangadi NO-05',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  1900.00,  20.00,  2280.00, true, 0),
('RTDK0007', 'Iron Wangadi NO-04',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  1450.00,  15.00,  1667.50, true, 0),
('RTDK0008', 'Iron Wangadi NO-03',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  1300.00,  20.00,  1560.00, true, 0),
('RTDK0009', 'Black Kettle NO-05',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),   725.00,  20.00,   870.00, true, 0),
('RTDK0010', 'Iron Wangadi NO-02',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),   900.00,  20.00,  1080.00, true, 0),
('RTDK0011', 'Rotty Pan (M)',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  1000.00,  20.00,  1200.00, true, 0),
('RTDK0012', 'Thachi 3D',                       'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  1800.00,  20.00,  2160.00, true, 0),
('RTDK0013', 'Dabara Set',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'), 12500.00,  20.00, 15000.00, true, 0),
('RTDK0014', 'Nany Basket',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  1002.00,  25.00,  1252.50, true, 0),
('RTDK0015', 'Medi Box',                        'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),   459.00,  25.00,   573.75, true, 0),
('RTDK0016', 'Drawer 4Pcs (Uni)',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  6810.00,  25.00,  8512.50, true, 0),
('RTDK0017', 'Baby Chair (BC 01)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),   900.00,  25.00,  1125.00, true, 0),
('RTDK0018', 'Baby Potty With Box',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  2295.00,  20.00,  2754.00, true, 0),
('RTDK0019', 'MS Rack 3 Layers',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='RT&DK CONSUMERS(PVT)LTD'),  3200.00,  20.00,  3840.00, true, 0),

-- ---- SNS PLASTICS (SNS0001–SNS0027) ----
('SNS0001', '3LTR Bucket With Lid (SNS)',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   84.00,  20.00,  100.80, true, 0),
('SNS0002', '5LTR Bucket With Lid (SNS)',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),  108.00,  20.00,  129.60, true, 0),
('SNS0003', '6001 Flower Pot',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   80.00,  25.00,  100.00, true, 0),
('SNS0004', 'King Pot',                      'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   70.00,  25.00,   87.50, true, 0),
('SNS0005', 'Fruit Basket (SNS)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),  140.00,  25.00,  175.00, true, 0),
('SNS0006', 'Book Rack 7 Pcs',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'), 1800.00,  25.00, 2250.00, true, 0),
('SNS0007', 'Book Rack 5 Pcs',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'), 1350.00,  25.00, 1687.50, true, 0),
('SNS0008', '6040 Meti Coppa NO-02',        'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   27.00,  25.00,   33.75, true, 0),
('SNS0009', '6005 Flower Pot',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   25.00,  25.00,   31.25, true, 0),
('SNS0010', '6003 Dustbin',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   67.00,  25.00,   83.75, true, 0),
('SNS0011', '1.5LIT Beaker NO-01 (SNS)',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   47.00,  25.00,   58.75, true, 0),
('SNS0012', '1.5LIT Beaker NO-02 (SNS)',    'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   30.00,  25.00,   37.50, true, 0),
('SNS0013', 'Plate SNS',                     'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   32.00,  25.00,   40.00, true, 0),
('SNS0014', 'Flower Plate 5002',             'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   29.00,  37.93,   40.00, true, 0),
('SNS0015', 'Lovely Basket',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   50.00,  25.00,   62.50, true, 0),
('SNS0016', 'Silky Bowl',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   35.00,  25.00,   43.75, true, 0),
('SNS0017', 'Amazone (Single)',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   30.00,  25.00,   37.50, true, 0),
('SNS0018', 'Brush Holder (SNS)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   97.00,  25.00,  121.25, true, 0),
('SNS0019', 'Frutty Water Jug',              'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),  105.00,  25.00,  131.25, true, 0),
('SNS0020', 'SNS Tea Cup',                   'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   25.00,  25.00,   31.25, true, 0),
('SNS0021', '3 In 1 Soap Holder',           'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   35.00,  25.00,   43.75, true, 0),
('SNS0022', 'Bucket 25LIT (SNS)',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),  365.00,  25.00,  456.25, true, 0),
('SNS0023', 'Lunch Box SNS',                 'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),  115.00,  25.00,  143.75, true, 0),
('SNS0024', '5001 Basin',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   35.00,  20.00,   42.00, true, 0),
('SNS0025', 'Tray 777 NO-02',               'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),  110.00,  20.00,  132.00, true, 0),
('SNS0026', 'Curry Dish',                    'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   10.00,  20.00,   12.00, true, 0),
('SNS0027', 'Jeshmin 3 In 1 (SNS)',         'PLASTIC', (SELECT id FROM inv_brands WHERE name='SNS PLASTICS'),   35.00,  20.00,   42.00, true, 0),

-- ---- NO BRAND IN SOURCE DATA — assigned to generic Plastic (Own) brand ----
('OWN0001', 'Baby Rack (S)',       'PLASTIC', (SELECT id FROM inv_brands WHERE name='Plastic (Own)'),  575.00,  20.00,  690.00, true, 0),
('OWN0002', 'Dust Pan With Brush', 'PLASTIC', (SELECT id FROM inv_brands WHERE name='Plastic (Own)'),  120.00,  20.00,  144.00, true, 0),
('OWN0003', 'Cone Mob',            'PLASTIC', (SELECT id FROM inv_brands WHERE name='Plastic (Own)'),  170.00,  20.00,  204.00, true, 0);

-- ============================================================
-- Source: ghanim-wholesale V7 (rainco items)
-- V7 — Rainco product items
-- item_code: RC- prefix + first code from product name; N suffix = New, O = old, F = Free, NL = New Latest

INSERT INTO inv_items (item_code, description, category, brand_id, mrp, margin_pct, wholesale_price, active, stock_qty) VALUES
('RC-1015',    '1015 -Teen Umbrella 1020',                                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   811.80, true, 0),
('RC-1020',    '1020-Printed Polyester-2F-Steel Frame',                   'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   729.80, true, 0),
('RC-1020N',   '1020-New Printed Polyester-2F-Steel Frame',               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   779.00, true, 0),
('RC-1040',    '1040-Printed Polyester-2F-Steel Frame',                   'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   779.00, true, 0),
('RC-1040F',   'Free 1040-Printed Polyester-2F-Steel Frame',              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   811.80, true, 0),
('RC-1040N',   '1040-New-Printed Polyester-2F-Steel Frame',               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   811.80, true, 0),
('RC-1041',    '1041-Black Polyester-2F-Steel Frame-Curv',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   861.00, true, 0),
('RC-1042',    '1042-Maroon Polyester-2F-Steel Frame-Cur',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   811.80, true, 0),
('RC-1044',    '1044-Yellow Polyester-2F-Steel Frame-Cur',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   861.00, true, 0),
('RC-1044N',   '1044-New-Yellow Polyester-2F-Steel Frame-Cur',            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   811.80, true, 0),
('RC-1045',    '1045-Black Polyester-2F-Steel Frame',                     'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   828.20, true, 0),
('RC-1045N',   '1045 New-Black Polyester-2F-Steel Frame',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   844.60, true, 0),
('RC-1045NL',  '1045 New Latest Black Polyester-2F-Steel Frame',          'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   943.00, true, 0),
('RC-1046',    '1046-Silver Coated Polyester-2F-Steel Fr',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   943.00, true, 0),
('RC-1047',    '1047-Printed Satin-2F-Steel Frame',                       'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   861.00, true, 0),
('RC-1047N',   '1047 New-Printed Satin-2F-Steel Frame',                   'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   893.80, true, 0),
('RC-1060',    '1060-Kids-Printed Polyester-Steel Frame',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   729.80, true, 0),
('RC-1070',    '1070-Printed Polyester-3F-Steel Fram',                    'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   975.80, true, 0),
('RC-1070N',   '1070 New printed polyester 3F',                           'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1008.60, true, 0),
('RC-1071',    '1071-Silver Coated Nylon-3F-Steel Frame',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1082.40, true, 0),
('RC-1071N',   '1071 New-Silver Coated Nylon-3F-Steel Frame',             'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1090.60, true, 0),
('RC-1073BU',  '1073BU-Butterfly Umbrella',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1139.80, true, 0),
('RC-1075',    '1075-Black Polyester-3F-Steel Frame',                     'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   975.80, true, 0),
('RC-1075N',   '1075 New Black Polyester-3F-Steel Frame',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1008.60, true, 0),
('RC-1076',    '1076-Silver Coated Polyester-3F-Steel Fr',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1139.80, true, 0),
('RC-1077',    '1077-Printed Satin-3F-Steel Frame',                       'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1057.80, true, 0),
('RC-1077N',   '1077- New Printed Satin-3F-Steel Frame',                  'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1082.40, true, 0),
('RC-1090',    '1090-Nano-Solid Colour Pongee-3F-Steel F',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1107.00, true, 0),
('RC-1146',    '1146-Silver Coated Black Polyester-2F-St',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   893.80, true, 0),
('RC-1146N',   '1146 New-Silver Coated Black Polyester-2F-St',            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   918.40, true, 0),
('RC-1160',    '1160-Kids-Printed Polyester-Steel Frame',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   811.80, true, 0),
('RC-1160N',   '1160 New-Kids-Printed Polyester-Steel Frame',             'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   836.40, true, 0),
('RC-1260',    '1260-EARLY TEEN',                                         'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   975.80, true, 0),
('RC-1350',    '1350-Curved Handle Ladies Umbrella',                      'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1303.80, true, 0),
('RC-1440',    '1440-Printed Polyester-Frill-2F-Steel Fr',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   893.80, true, 0),
('RC-1440O',   '1440-old-Printed Polyester-Frill-2F-Steel Fr',            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1000.40, true, 0),
('RC-1470',    '1470-Printed Polyester-Frill-3F-Steel Fr',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1057.80, true, 0),
('RC-1648',    '1648-Sunproof Umbrella 2 Fold',                           'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1221.80, true, 0),
('RC-1759M',   '1759M-Mothers Day Umbrella',                              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2451.80, true, 0),
('RC-1774',    '1774-Ditsy Floral',                                       'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1139.80, true, 0),
('RC-1776',    '1776-Sun Block Range - Solid Color',                      'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1082.40, true, 0),
('RC-1776N',   '1776 New-Sun Block Range - Solid Color',                  'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1107.00, true, 0),
('RC-1778',    '1778-Sun proof collection - 2018 Materia',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1549.80, true, 0),
('RC-2501',    '2501-Black Polyester-Steel Frame-Curve H',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1025.00, true, 0),
('RC-2502',    '2502-Maroon Polyester-Steel Frame-Curve',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1107.00, true, 0),
('RC-2504',    '2504-Yellow Polyester-Steel Frame-Curve',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1025.00, true, 0),
('RC-2504N',   '2504-New-Yellow Polyester-Steel Frame-Curve',             'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1057.80, true, 0),
('RC-2563',    '2563-Multi Colour Polyester-24"-Steel Fr',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1312.00, true, 0),
('RC-2563N',   '2563 New-Multi Colour Polyester-24"-Steel Fr',            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1344.80, true, 0),
('RC-2571',    '2571-Black Polyester-27"-Steel Frame',                    'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1385.80, true, 0),
('RC-2573',    '2573-Multi Colour Polyester-27"-Steel Fr',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1353.00, true, 0),
('RC-2573N',   '2573 New-Multi Colour Polyester-27"-Steel Fr',            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1385.80, true, 0),
('RC-2583',    '2583-Multi Colour Polyester-30"-Steel Fr',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1549.80, true, 0),
('RC-2583N',   '2583 New-Multi Colour Polyester-30"-Steel Fr',            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1580.90, true, 0),
('RC-2584',    '2584-Muthu Kuda-Plain Yellow Polyester-2',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2583.00, true, 0),
('RC-2584N',   '2584 New-Muthu Kuda-Plain Yellow Polyester-2',            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2624.00, true, 0),
('RC-2585',    '2585-Muthu Kuda-Decorated Yellow Polyest',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  3271.80, true, 0),
('RC-2585N',   '2585-New-Muthu Kuda-Decorated Yellow Polyest',            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  3435.80, true, 0),
('RC-2763',    '2763-AUTO OPEN 24',                                       'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1303.80, true, 0),
('RC-2764',    '2764 - Curve handle 24 Gents - 41080',                    'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1385.80, true, 0),
('RC-3830',    '3830-Golden Bed Net-Single',                              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1631.80, true, 0),
('RC-3830N',   '3830 New-Golden Bed Net-Single',                          'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1681.00, true, 0),
('RC-3831',    '3831-Golden Bed Net-Double',                              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1959.80, true, 0),
('RC-3831N',   '3831 New-Golden Bed Net-Double',                          'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2009.00, true, 0),
('RC-3832',    '3832-Golden Bed Net-Queen',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2287.80, true, 0),
('RC-3832N',   '3832 New-Golden Bed Net-Queen',                           'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2337.00, true, 0),
('RC-3833N',   '3833 New -Golden Bed Net-King',                           'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2419.00, true, 0),
('RC-3860',    '3860-Freedom-Bed Net-Single',                             'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2619.90, true, 0),
('RC-3860N',   '3860N-Freedom-Bed Net-Single',                            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2779.80, true, 0),
('RC-3861',    '3861-Freedom-Bed Net-Double-Queen',                       'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2865.90, true, 0),
('RC-3861N',   '3861N-Freedom-Bed Net-Double-Queen',                      'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  3025.80, true, 0),
('RC-3862N',   '3862N-Freedom-Bed Net-King',                              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  3271.80, true, 0),
('RC-3863N',   '3863N-Freedom-Bed Net-California King',                   'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  3517.80, true, 0),
('RC-3880',    '3880-Laser-Bed Net-Single',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   984.00, true, 0),
('RC-3881',    '3881-Laser-Bed Net-Double',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1107.00, true, 0),
('RC-3882',    '3882-Laser-Bed Net-Queen',                                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1221.00, true, 0),
('RC-3883',    '3883-Laser-Bed Net-King',                                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1385.00, true, 0),
('RC-3890',    '3890-Pearl Bed Net-Single',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2501.00, true, 0),
('RC-3891',    '3891-Pearl Bed Net-Double',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2747.00, true, 0),
('RC-3892',    '3892-Pearl Bed Net-Queen',                                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2993.00, true, 0),
('RC-3893',    '3893-Pearl Bed Net-King-California King',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  3239.00, true, 0),
('RC-3910',    '3910-Singithi Baby Plain',                                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   623.20, true, 0),
('RC-3910N',   '3910-New-Singithi Baby Plain',                            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   647.80, true, 0),
('RC-3912',    '3912-Singithi Toddler Plain',                             'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   877.40, true, 0),
('RC-3912N',   '3912-New-Singithi Toddler Plain',                         'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   893.80, true, 0),
('RC-3913',    '3913-Singithi Baby Printed',                              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   729.80, true, 0),
('RC-3913N',   '3913-New-Singithi Baby Printed',                          'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   754.40, true, 0),
('RC-3941',    '3941-Comfort Net-Single-Double',                          'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  5912.20, true, 0),
('RC-3941N',   '3941-New-Comfort Net-Single-Double',                      'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  4911.80, true, 0),
('RC-3942',    '3942-Comfort-Bed Net-Queen',                              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  6445.20, true, 0),
('RC-3942N',   '3942-New-Comfort-Bed Net-Queen',                          'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  5321.80, true, 0),
('RC-3943',    '3943-Comfort-Bed Net-King',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  5895.80, true, 0),
('RC-4321',    '4321-DREAM RAINCOAT - MEDIUM',                            'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2041.80, true, 0),
('RC-4768',    '4768-Multi Colour Oxford-40"-Steel Frame',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  6773.20, true, 0),
('RC-4778',    '4778-Multi Colour Oxford-44-Steel Frame',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  7429.20, true, 0),
('RC-4778N',   '4778-New Multi Colour Oxford-44-Steel Frame',             'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  7453.80, true, 0),
('RC-5020',    '5020-Super Force-Rain Coat-Small',                        'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2451.80, true, 0),
('RC-5021',    '5021-Super Force-Rain Coat-Medium',                       'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2451.80, true, 0),
('RC-5023',    '5023-Super Force-Rain Coat-Extra Large',                  'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2451.80, true, 0),
('RC-5050',    '5050-Super Force-Rain Suit-Two Pcs-Small',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2779.80, true, 0),
('RC-5051',    '5051-Super Force-Rain Suit-Two Pcs-Mediu',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2779.80, true, 0),
('RC-5052',    '5052-Super Force-Rain Suit-Two Pcs-Large',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2779.80, true, 0),
('RC-5053',    '5053-Super Force-Rain Suit-Two Pcs-Extra',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2779.80, true, 0),
('RC-11600',   '11600-Kids-Luminous Polyester-Steel Fram',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   893.80, true, 0),
('RC-15051',   '15051-ZEEL GENTS RAIN SUIT (RF111)- M',                  'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2369.80, true, 0),
('RC-15052',   '15052-ZEEL GENTS RAIN SUIT CARE (RF111)',                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2287.80, true, 0),
('RC-15950',   '15950 RAINTOP- PONCHO(FREE SIZE)',                        'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   893.80, true, 0),
('RC-25736',   '25736-Inside Silver Golf Umbrella - 27',                  'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1435.00, true, 0),
('RC-CPJ6050', 'CPJ6050 junior raincoat S',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   902.00, true, 0),
('RC-CPJ6051', 'CPJ6051 junior raincoat M',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   943.00, true, 0),
('RC-CPJ6052', 'CPJ6052 junior raincoat L',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1008.00, true, 0),
('RC-CPJ6053', 'CPJ6053 junior raincoat XL',                              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1049.00, true, 0),
('RC-K01040',  'K01040-Regular Printed 2F',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   647.80, true, 0),
('RC-K01045',  'K01045-Regular Black 2F',                                 'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   647.80, true, 0),
('RC-K01047',  'K01047-Regular Printed Satin 2F',                         'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   697.00, true, 0),
('RC-K01070',  'K01070-Regular Printed 3F',                               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   836.40, true, 0),
('RC-K01160',  'K01160-Solo Kids',                                        'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,   574.00, true, 0),
('RC-K02563',  'K02563-Solo Gents',                                       'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1033.20, true, 0),
('RC-TM1392',  'TM1392-1390 LIDL FRAME WITH TEFLON FABRI',               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  2583.00, true, 0),
('RC-TM1644',  'TM1644-1640 FRAME WITH PONGEE FABRIC 2F',                'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1000.40, true, 0),
('RC-TM1771',  'TM1771-1770 FRAME WITH NYLON SILVER FABR',               'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1336.60, true, 0),
('RC-TM2673',  'TM2673-Solid Colour Pongee-27"-Fiber Frame',              'RAINCO', (SELECT id FROM inv_brands WHERE name='Rainco'), NULL, 18.00,  1631.80, true, 0);

-- ============================================================
-- Source: ghanim-wholesale V9 (stationery sub-brands; ALTER dropped)
-- V9 — Split stationery into sub-brands with correct discount slabs
--      and add free-issue tracking columns to items.
--
-- Sub-brands created (all under STATIONERY category, principal STATIONERY_AGENT):
--   Hauser           — SLAB  (25 / 27 / 29 / 32 %)
--   Socks            — SLAB  (30 / 32.5 / 35 %)
--   Lunch Box        — SLAB  (25 / 27.5 / 30 %)
--   Shoe Polish      — SLAB  (17 % flat, single tier)
--
-- Free-issue rules added (per item):
--   8950–8956  buy 12 → 3 free
--   8957       buy 12 → 1 free

-- ── 1. Add free-issue columns to items ──────────────────────────────────────
-- ── 2. Create stationery sub-brands ─────────────────────────────────────────
INSERT INTO inv_brands (name, brand_code, category, principal, discount_type, default_margin_pct) VALUES
    ('Hauser',      'HAU',  'STATIONERY', 'STATIONERY_AGENT', 'SLAB', NULL),
    ('Socks',       'SCK',  'STATIONERY', 'STATIONERY_AGENT', 'SLAB', NULL),
    ('Lunch Box',   'LBX',  'STATIONERY', 'STATIONERY_AGENT', 'SLAB', NULL),
    ('Shoe Polish', 'SPL',  'STATIONERY', 'STATIONERY_AGENT', 'SLAB', 17.00);

-- ── 3. Discount slabs — Hauser (right-image scheme) ─────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id,      0,      34999.99, 25.00, 1 FROM inv_brands WHERE name = 'Hauser' UNION ALL
SELECT id,  35000,      69999.99, 27.00, 2 FROM inv_brands WHERE name = 'Hauser' UNION ALL
SELECT id,  70000,     139999.99, 29.00, 3 FROM inv_brands WHERE name = 'Hauser' UNION ALL
SELECT id, 140000,          NULL, 32.00, 4 FROM inv_brands WHERE name = 'Hauser';

-- ── 4. Discount slabs — Socks ────────────────────────────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id,      0,      99999.99, 30.00, 1 FROM inv_brands WHERE name = 'Socks' UNION ALL
SELECT id, 100000,     249999.99, 32.50, 2 FROM inv_brands WHERE name = 'Socks' UNION ALL
SELECT id, 250000,          NULL, 35.00, 3 FROM inv_brands WHERE name = 'Socks';

-- ── 5. Discount slabs — Lunch Box ───────────────────────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id,      0,      99999.99, 25.00, 1 FROM inv_brands WHERE name = 'Lunch Box' UNION ALL
SELECT id, 100000,     199999.99, 27.50, 2 FROM inv_brands WHERE name = 'Lunch Box' UNION ALL
SELECT id, 200000,          NULL, 30.00, 3 FROM inv_brands WHERE name = 'Lunch Box';

-- ── 6. Discount slabs — Shoe Polish (flat 17 %) ─────────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id, 0, NULL, 17.00, 1 FROM inv_brands WHERE name = 'Shoe Polish';

-- ── 7. Re-point existing stationery items to their correct sub-brand ─────────

-- Hauser stationery (pencils, erasers, sharpeners, colour pencils, oil pastels, rulers, math sets)
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Hauser')
WHERE item_code IN (
    '9000','9001','9002','9003',
    '9010','9011','9012','9013',
    '9020','9021',
    '9030','9031',
    '9040','9041',
    '9050','9051',
    '9060','9061'
);

-- Socks
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Socks')
WHERE item_code IN (
    '18600','18610','18620','18630',
    '18601','18611','18621','18631',
    '18730','18731','18732','18733',
    '18500','18501','18502','18503',
    '18510','18511','18512','18513',
    '18400','18401','18402','18403',
    '18410','18411','18412','18413'
);

-- Lunch Box
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Lunch Box')
WHERE item_code IN ('J7010','J7011','J7012');

-- Shoe Polish (8950–8958)
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Shoe Polish')
WHERE item_code IN ('8950','8951','8952','8953','8954','8955','8956','8957','8958');

-- ── 8. Free-issue rules for Shoe Polish ─────────────────────────────────────
-- Pro Silver Wax (36g) and Liquid (75ml/40ml): buy 12 → 3 free
UPDATE inv_items
SET free_issue_buy_qty = 12, free_issue_free_qty = 3
WHERE item_code IN ('8950','8951','8952','8953','8954','8955','8956');

-- Pro Silver Shoe Shine Brush: buy 12 → 1 free
UPDATE inv_items
SET free_issue_buy_qty = 12, free_issue_free_qty = 1
WHERE item_code = '8957';

-- ============================================================
-- Source: ghanim-wholesale V10 (rainco sub-brands)
-- V10 — Replace generic Rainco brand with 6 sub-brands, each with correct slabs.
--
-- Sub-brands (RAINCO category, principal RAINCO, discount_type SLAB, margin 18%):
--   Umbrella        — 8 tiers
--   Bet Net         — 8 tiers
--   Singithi        — 8 tiers (first definition: starting from 0)
--   Adult Raincoat  — 8 tiers
--   Kids Raincoat   — 8 tiers
--   Kuda            — same slabs as Umbrella (confirm if different)

-- ── 1. Retire old generic brand's slabs ─────────────────────────────────────
DELETE FROM inv_discount_slabs
WHERE brand_id = (SELECT id FROM inv_brands WHERE name = 'Rainco');

UPDATE inv_brands SET name = 'Rainco (Generic)' WHERE name = 'Rainco';

-- ── 2. Create Rainco sub-brands ──────────────────────────────────────────────
INSERT INTO inv_brands (name, brand_code, category, principal, discount_type, default_margin_pct) VALUES
    ('Umbrella',       'RC-UMB', 'RAINCO', 'RAINCO', 'SLAB', 18.00),
    ('Bet Net',        'RC-BN',  'RAINCO', 'RAINCO', 'SLAB', 18.00),
    ('Singithi',       'RC-SNG', 'RAINCO', 'RAINCO', 'SLAB', 18.00),
    ('Adult Raincoat', 'RC-ARC', 'RAINCO', 'RAINCO', 'SLAB', 18.00),
    ('Kids Raincoat',  'RC-KRC', 'RAINCO', 'RAINCO', 'SLAB', 18.00),
    ('Kuda',           'RC-K0',  'RAINCO', 'RAINCO', 'SLAB', 18.00);

-- ── 3. Umbrella slabs ────────────────────────────────────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id,       0,      22999.99, 0.00, 1 FROM inv_brands WHERE name = 'Umbrella' UNION ALL
SELECT id,   23000,      37499.99, 1.00, 2 FROM inv_brands WHERE name = 'Umbrella' UNION ALL
SELECT id,   37500,      59999.99, 2.00, 3 FROM inv_brands WHERE name = 'Umbrella' UNION ALL
SELECT id,   60000,     134999.99, 3.50, 4 FROM inv_brands WHERE name = 'Umbrella' UNION ALL
SELECT id,  135000,     299999.99, 5.00, 5 FROM inv_brands WHERE name = 'Umbrella' UNION ALL
SELECT id,  300000,     599999.99, 6.00, 6 FROM inv_brands WHERE name = 'Umbrella' UNION ALL
SELECT id,  600000,    1049999.99, 7.00, 7 FROM inv_brands WHERE name = 'Umbrella' UNION ALL
SELECT id, 1050000,          NULL, 8.00, 8 FROM inv_brands WHERE name = 'Umbrella';

-- ── 4. Bet Net slabs ─────────────────────────────────────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id,      0,      7999.99, 0.00, 1 FROM inv_brands WHERE name = 'Bet Net' UNION ALL
SELECT id,   8000,     23999.99, 2.00, 2 FROM inv_brands WHERE name = 'Bet Net' UNION ALL
SELECT id,  24000,     39999.99, 2.50, 3 FROM inv_brands WHERE name = 'Bet Net' UNION ALL
SELECT id,  40000,     79999.99, 3.00, 4 FROM inv_brands WHERE name = 'Bet Net' UNION ALL
SELECT id,  80000,    119999.99, 4.00, 5 FROM inv_brands WHERE name = 'Bet Net' UNION ALL
SELECT id, 120000,    159999.99, 5.00, 6 FROM inv_brands WHERE name = 'Bet Net' UNION ALL
SELECT id, 160000,    239999.99, 6.00, 7 FROM inv_brands WHERE name = 'Bet Net' UNION ALL
SELECT id, 240000,          NULL, 8.00, 8 FROM inv_brands WHERE name = 'Bet Net';

-- ── 5. Singithi slabs (first definition, starting from 0) ───────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id,     0,      4999.99, 0.00, 1 FROM inv_brands WHERE name = 'Singithi' UNION ALL
SELECT id,  5000,      9499.99, 1.00, 2 FROM inv_brands WHERE name = 'Singithi' UNION ALL
SELECT id,  9500,     16499.99, 2.00, 3 FROM inv_brands WHERE name = 'Singithi' UNION ALL
SELECT id, 16500,     24999.99, 3.00, 4 FROM inv_brands WHERE name = 'Singithi' UNION ALL
SELECT id, 25000,     32999.99, 4.00, 5 FROM inv_brands WHERE name = 'Singithi' UNION ALL
SELECT id, 33000,     49999.99, 5.00, 6 FROM inv_brands WHERE name = 'Singithi' UNION ALL
SELECT id, 49500,     99999.99, 6.00, 7 FROM inv_brands WHERE name = 'Singithi' UNION ALL
SELECT id, 100000,        NULL, 8.00, 8 FROM inv_brands WHERE name = 'Singithi';

-- ── 6. Adult Raincoat slabs ──────────────────────────────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id,      0,      7999.99, 0.00, 1 FROM inv_brands WHERE name = 'Adult Raincoat' UNION ALL
SELECT id,   8000,     23999.99, 1.00, 2 FROM inv_brands WHERE name = 'Adult Raincoat' UNION ALL
SELECT id,  24000,     39999.99, 2.00, 3 FROM inv_brands WHERE name = 'Adult Raincoat' UNION ALL
SELECT id,  40000,     79999.99, 2.50, 4 FROM inv_brands WHERE name = 'Adult Raincoat' UNION ALL
SELECT id,  80000,    119999.99, 3.00, 5 FROM inv_brands WHERE name = 'Adult Raincoat' UNION ALL
SELECT id, 120000,    159999.99, 4.00, 6 FROM inv_brands WHERE name = 'Adult Raincoat' UNION ALL
SELECT id, 160000,    319999.99, 6.00, 7 FROM inv_brands WHERE name = 'Adult Raincoat' UNION ALL
SELECT id, 320000,          NULL, 8.00, 8 FROM inv_brands WHERE name = 'Adult Raincoat';

-- ── 7. Kids Raincoat slabs ───────────────────────────────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id,     0,     4199.99, 0.00, 1 FROM inv_brands WHERE name = 'Kids Raincoat' UNION ALL
SELECT id,  4200,     7999.99, 1.00, 2 FROM inv_brands WHERE name = 'Kids Raincoat' UNION ALL
SELECT id,  8000,     8499.99, 2.00, 3 FROM inv_brands WHERE name = 'Kids Raincoat' UNION ALL
SELECT id,  8500,    13999.99, 3.00, 4 FROM inv_brands WHERE name = 'Kids Raincoat' UNION ALL
SELECT id, 14000,    20999.99, 4.00, 5 FROM inv_brands WHERE name = 'Kids Raincoat' UNION ALL
SELECT id, 21000,    30999.99, 6.00, 6 FROM inv_brands WHERE name = 'Kids Raincoat' UNION ALL
SELECT id, 31000,    41999.99, 7.00, 7 FROM inv_brands WHERE name = 'Kids Raincoat' UNION ALL
SELECT id, 42000,         NULL, 8.00, 8 FROM inv_brands WHERE name = 'Kids Raincoat';

-- ── 8. Kuda slabs (flat 11% for any amount) ─────────────────────────────────
INSERT INTO inv_discount_slabs (brand_id, min_value, max_value, discount_pct, sort_order)
SELECT id, 0, NULL, 11.00, 1 FROM inv_brands WHERE name = 'Kuda';

-- ── 9. Assign items ──────────────────────────────────────────────────────────

-- Bet Net (all bed net items: 38xx series + 3941/3942/3943 Comfort Nets)
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Bet Net')
WHERE item_code IN (
    'RC-3830','RC-3830N','RC-3831','RC-3831N','RC-3832','RC-3832N','RC-3833N',
    'RC-3860','RC-3860N','RC-3861','RC-3861N','RC-3862N','RC-3863N',
    'RC-3880','RC-3881','RC-3882','RC-3883',
    'RC-3890','RC-3891','RC-3892','RC-3893',
    'RC-3941','RC-3941N','RC-3942','RC-3942N','RC-3943'
);

-- Singithi (baby/toddler nets: 3910, 3912, 3913 with N variants)
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Singithi')
WHERE item_code IN (
    'RC-3910','RC-3910N','RC-3912','RC-3912N','RC-3913','RC-3913N'
);

-- Kids Raincoat (CPJ6050 series)
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Kids Raincoat')
WHERE item_code IN (
    'RC-CPJ6050','RC-CPJ6051','RC-CPJ6052','RC-CPJ6053'
);

-- Adult Raincoat
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Adult Raincoat')
WHERE item_code IN (
    'RC-4321',
    'RC-5020','RC-5021','RC-5023',
    'RC-5050','RC-5051','RC-5052','RC-5053',
    'RC-15051','RC-15052','RC-15950'
);

-- Kuda (K0 series: compact/folding umbrellas)
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Kuda')
WHERE item_code IN (
    'RC-K01040','RC-K01045','RC-K01047','RC-K01070','RC-K01160','RC-K02563'
);

-- Umbrella = all remaining RAINCO items still pointing at 'Rainco (Generic)'
UPDATE inv_items SET brand_id = (SELECT id FROM inv_brands WHERE name = 'Umbrella')
WHERE category = 'RAINCO'
  AND brand_id = (SELECT id FROM inv_brands WHERE name = 'Rainco (Generic)');

-- ============================================================
-- Source: ghanim-wholesale V11 (rainco MRP backfill)
-- V11 — Populate MRP for all Rainco items.
--
-- Formula: MRP = wholesale_price / (1 - margin_pct/100)
-- Rounded to nearest 10 (Rainco's pricing convention).
-- wholesale_price is used directly as WSP in invoicing (DiscountEngineService),
-- so MRP is a display-only field — no invoice totals are affected.

UPDATE inv_items
SET mrp = ROUND(wholesale_price / (1.0 - margin_pct / 100.0) / 10) * 10
WHERE category = 'RAINCO'
  AND mrp IS NULL
  AND wholesale_price IS NOT NULL
  AND margin_pct IS NOT NULL;
