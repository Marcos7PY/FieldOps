-- P2-24: Index on created_at for range filtering and default order listing sorting
CREATE INDEX ix_work_order_created_at ON work_order (created_at DESC);
