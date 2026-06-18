ALTER TABLE bill_stock_items ADD COLUMN backorder BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stock_reduction_pending_items ADD COLUMN backorder BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stock_reduction_pending_items ADD COLUMN predicted_unit_price DECIMAL(19,2);
