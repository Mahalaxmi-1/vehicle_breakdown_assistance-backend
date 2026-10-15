-- Additive service-category data required by the request form and admin page.
CREATE TABLE IF NOT EXISTS service_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    description VARCHAR(150) NOT NULL,
    active BIT NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    CONSTRAINT uk_service_categories_name UNIQUE (name)
);

INSERT IGNORE INTO service_categories (name, description, active) VALUES
    ('Battery', 'Battery inspection and jump start', b'1'),
    ('Tyre', 'Puncture repair and tyre assistance', b'1'),
    ('Engine', 'Engine troubleshooting and repair', b'1'),
    ('Fuel', 'Emergency fuel assistance', b'1'),
    ('Electrical', 'Electrical system troubleshooting', b'1'),
    ('Other', 'Other roadside assistance', b'1');
