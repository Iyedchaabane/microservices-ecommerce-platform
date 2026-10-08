package com.ichaabane.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.ichaabane.ecommerce.customer.CustomerResponse;
import com.ichaabane.ecommerce.order.dto.OrderRequest;
import com.ichaabane.ecommerce.order.model.PaymentMethod;
import com.ichaabane.ecommerce.order.repository.OrderRepository;
import com.ichaabane.ecommerce.orderline.repository.OrderLineRepository;
import com.ichaabane.ecommerce.product.PurchaseRequest;
import com.ichaabane.ecommerce.product.PurchaseResponse;
import com.ichaabane.ecommerce.stubs.CustomerClientStub;
import com.ichaabane.ecommerce.stubs.PaymentClientStub;
import com.ichaabane.ecommerce.stubs.ProductClientStub;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderLineRepository orderLineRepository;

	private static WireMockServer wireMockServer;

	// Stubs
	private CustomerClientStub customerStub;
	private ProductClientStub productStub;
	private PaymentClientStub paymentStub;

	@BeforeAll
	static void setupWireMock() {
		wireMockServer = new WireMockServer(8089);
		wireMockServer.start();
	}

	@AfterAll
	static void tearDownWireMock() {
		wireMockServer.stop();
	}

	@BeforeEach
	void setup() {
		wireMockServer.resetAll();

		// Initialiser les stubs
		customerStub = new CustomerClientStub(wireMockServer, objectMapper);
		productStub = new ProductClientStub(wireMockServer, objectMapper);
		paymentStub = new PaymentClientStub(wireMockServer);
	}

	@AfterEach
	void cleanDatabase() {
		// Les lignes référencent la commande (FK) : elles doivent partir en premier
		orderLineRepository.deleteAll();
		orderRepository.deleteAll();
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("application.config.customer-url", () -> "http://localhost:8089/api/v1/customers");
		registry.add("application.config.product-url", () -> "http://localhost:8089/api/v1/products");
		registry.add("application.config.payment-url", () -> "http://localhost:8089/api/v1/payments");
	}

	@Test
	@DisplayName("Devrait créer une commande avec succès")
	void shouldCreateOrderSuccessfully() throws Exception {
		// ARRANGE
		CustomerResponse customerResponse = new CustomerResponse(
				"CUST001",
				"John",
				"Doe",
				"john.doe@email.com"
		);

		List<PurchaseResponse> productResponses = List.of(
				new PurchaseResponse(1, "Laptop", "Dell XPS", BigDecimal.valueOf(1200), 2)
		);

		// Configurer les stubs
		customerStub.stubFindCustomerById_Success("CUST001", customerResponse);
		productStub.stubPurchaseProducts_Success(productResponses);
		paymentStub.stubRequestOrderPayment_Success(1);

		OrderRequest orderRequest = new OrderRequest(
				"ORD001",
				PaymentMethod.CREDIT_CARD,
				"CUST001",
				List.of(new PurchaseRequest(1, 2))
		);

		// ACT & ASSERT
		mockMvc.perform(post("/api/v1/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(orderRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isNumber());

		// Vérifier les appels
		customerStub.verifyFindCustomerByIdCalled("CUST001", 1);
		productStub.verifyPurchaseProductsCalled(1);
		paymentStub.verifyRequestOrderPaymentCalled(1);

		// Vérifier la base de données
		Assertions.assertEquals(1, orderRepository.count());
	}

	@Test
	@DisplayName("Devrait rejeter la commande quand le client est introuvable")
	void shouldRejectOrderWhenCustomerDoesNotExist() throws Exception {
		// ARRANGE : le customer-service répond 404 (le stock n'est pas touché)
		customerStub.stubFindCustomerById_NotFound("UNKNOWN");

		OrderRequest orderRequest = new OrderRequest(
				"ORD404",
				PaymentMethod.CREDIT_CARD,
				"UNKNOWN",
				List.of(new PurchaseRequest(1, 1))
		);

		// ACT & ASSERT : BusinessException => 400, et ni le produit ni le paiement
		// ne doivent être appelés (échec AVANT la réservation du stock)
		mockMvc.perform(post("/api/v1/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(orderRequest)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("No customer exists with the provided ID")));

		customerStub.verifyFindCustomerByIdCalled("UNKNOWN", 1);
		productStub.verifyPurchaseProductsCalled(0);
		paymentStub.verifyRequestOrderPaymentCalled(0);
		Assertions.assertEquals(0, orderRepository.count());
	}



	@Test
	@DisplayName("Devrait récupérer toutes les commandes")
	void shouldGetAllOrders() throws Exception {
		mockMvc.perform(get("/api/v1/orders"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}
}
