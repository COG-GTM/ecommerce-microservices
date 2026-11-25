-- Schema for order-service. Applied once by the MySQL image (as root) when the
-- data directory is first initialised; the application account cannot run DDL.
USE order_service;

CREATE TABLE IF NOT EXISTS t_orders (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS t_order_line_items (
    id       BIGINT         NOT NULL AUTO_INCREMENT,
    sku_code VARCHAR(255)   DEFAULT NULL,
    price    DECIMAL(38, 2) DEFAULT NULL,
    quantity INT            DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS t_orders_order_line_items_list (
    order_id                 BIGINT NOT NULL,
    order_line_items_list_id BIGINT NOT NULL,
    UNIQUE KEY uk_order_line_items_list_id (order_line_items_list_id),
    KEY fk_order_line_items_order (order_id),
    CONSTRAINT fk_order_line_items_order FOREIGN KEY (order_id) REFERENCES t_orders (id),
    CONSTRAINT fk_order_line_items_item FOREIGN KEY (order_line_items_list_id) REFERENCES t_order_line_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
