SET SESSION cte_max_recursion_depth = 10000;

INSERT INTO product (id, name, price, stock, updated_at)
WITH RECURSIVE seq(id) AS (
    SELECT 1
    UNION ALL
    SELECT id + 1 FROM seq WHERE id < 10000
)
SELECT
    id,
    CONCAT('benchmark-product-', id),
    10000 + id,
    100 + (id % 100),
    NOW()
FROM seq
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    stock = VALUES(stock),
    updated_at = VALUES(updated_at);
