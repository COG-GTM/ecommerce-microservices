package com.ibatulanand.orderservice;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestDatabaseCleaner {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void clear() {
        entityManager.createNativeQuery("delete from t_orders_order_line_items_list").executeUpdate();
        entityManager.createNativeQuery("delete from t_order_line_items").executeUpdate();
        entityManager.createNativeQuery("delete from t_orders").executeUpdate();
    }
}
