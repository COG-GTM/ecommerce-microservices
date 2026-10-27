// Products seeded into product-service; SKUs match the inventory-service seed data
// (iphone_15 has stock, iphone_15_pro has quantity 0).
export const IN_STOCK = { name: 'iPhone 15', description: 'In-stock E2E product', price: 799.99, skuCode: 'iphone_15' };
export const OUT_OF_STOCK = { name: 'iPhone 15 Pro', description: 'Sold-out E2E product', price: 999.0, skuCode: 'iphone_15_pro' };
