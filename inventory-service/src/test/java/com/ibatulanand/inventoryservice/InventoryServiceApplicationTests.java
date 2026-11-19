package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryServiceApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InventoryRepository inventoryRepository;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void stockCheckKeepsRowOrderAndDuplicatesAndOmitsUnknownSkus() throws Exception {
		inventoryRepository.saveAllAndFlush(List.of(
				new Inventory(null, "zebra-test-sku", 0),
				new Inventory(null, "ant-test-sku", 3),
				new Inventory(null, "zebra-test-sku", 7)));

		mockMvc.perform(get("/api/inventory")
						.param("skuCode", "ant-test-sku", "zebra-test-sku", "missing-test-sku", "zebra-test-sku"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						[
						  {"skuCode":"zebra-test-sku","inStock":false},
						  {"skuCode":"ant-test-sku","inStock":true},
						  {"skuCode":"zebra-test-sku","inStock":true}
						]
						""", true));
	}

}
