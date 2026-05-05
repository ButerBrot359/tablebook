CREATE TABLE tables (
    id            BIGSERIAL    PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL,
    label         VARCHAR(50)  NOT NULL,
    capacity      INT          NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_tables_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,

    CONSTRAINT chk_tables_capacity_positive
        CHECK (capacity > 0 AND capacity <= 50)
);

CREATE INDEX idx_tables_restaurant ON tables (restaurant_id);
