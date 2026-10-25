package io.github.adalberto.bedrockmcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import io.github.adalberto.bedrockmcp.backend.CatalogApiClient;
import io.github.adalberto.bedrockmcp.backend.CatalogGateway;
import io.github.adalberto.bedrockmcp.catalog.CatalogService;
import io.github.adalberto.bedrockmcp.catalog.CreateOrderRequest;
import io.github.adalberto.bedrockmcp.catalog.Order;
import io.github.adalberto.bedrockmcp.catalog.Product;

import org.junit.jupiter.api.Test;

class CatalogToolsTest {

    /** In-process fake of the HTTP client, backed by the sample service. */
    private final CatalogService service = new CatalogService();
    private final CatalogApiClient fakeClient = new CatalogApiClient() {
        @Override
        public List<Product> searchProducts(String query, String category) {
            return service.search(query, category);
        }

        @Override
        public Product getProduct(String id) {
            return service.find(id).orElseThrow();
        }

        @Override
        public Order createOrder(CreateOrderRequest request) {
            return service.createOrder(request);
        }
    };
    private final CatalogTools tools = new CatalogTools(new CatalogGateway(fakeClient));

    @Test
    void searchByCategory() {
        assertThat(tools.searchProducts(null, "camping")).extracting(Product::id).containsExactly("P-300", "P-301");
    }

    @Test
    void createOrderReducesStock() {
        Order order = tools.createOrder("P-100", 2);
        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(tools.getProduct("P-100").stock()).isEqualTo(23);
    }

    @Test
    void rejectsInvalidInputsBeforeCallingTheApi() {
        assertThatThrownBy(() -> tools.createOrder("P-100", 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.getProduct("../admin")).isInstanceOf(IllegalArgumentException.class);
    }
}
