ALTER TABLE return_products
    ADD COLUMN is_stock_product  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN is_return_product BOOLEAN NOT NULL DEFAULT FALSE;

-- Products with DAMAGE_IN movements → mark as return product
UPDATE return_products
SET is_return_product = TRUE
WHERE id IN (
    SELECT DISTINCT product_id FROM shadow_stock_movements WHERE type = 'DAMAGE_IN'
);

-- Products with ONLY DAMAGE_IN (no BILL_OUT) → remove from stock
UPDATE return_products
SET is_stock_product = FALSE
WHERE id IN (
    SELECT DISTINCT product_id FROM shadow_stock_movements WHERE type = 'DAMAGE_IN'
)
AND id NOT IN (
    SELECT DISTINCT product_id FROM shadow_stock_movements WHERE type = 'BILL_OUT'
);
