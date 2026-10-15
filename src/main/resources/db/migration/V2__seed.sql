-- Demo admin. Password: ChangeMe123! (bcrypt). Replace before any shared deployment.
INSERT INTO users (id, email, password_hash, full_name, role)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'admin@orderflow.dev',
    '$2b$10$xPy0x8qai5eeXrGTlPHaY.vmlQv8cETlgC6MBoxPDwCTe0fPhYyfK',
    'Orderflow Admin',
    'ADMIN'
);

INSERT INTO products (id, sku, name, description, unit_price, stock_quantity)
VALUES
    (
        '22222222-2222-2222-2222-222222222201',
        'OF-KBD-01',
        'Mechanical Keyboard',
        'Tactile switches, tenkeyless.',
        129.00,
        50
    ),
    (
        '22222222-2222-2222-2222-222222222202',
        'OF-MSE-01',
        'Wireless Mouse',
        'Silent clicks, USB-C charging.',
        49.00,
        80
    ),
    (
        '22222222-2222-2222-2222-222222222203',
        'OF-HUB-01',
        'USB-C Hub',
        'HDMI, USB-A, and power delivery.',
        79.50,
        40
    );
