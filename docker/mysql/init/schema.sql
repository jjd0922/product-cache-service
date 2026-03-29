CREATE TABLE IF NOT EXISTS product (
    id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    stock INT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_product_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_product_stock_non_negative CHECK (stock >= 0)
);