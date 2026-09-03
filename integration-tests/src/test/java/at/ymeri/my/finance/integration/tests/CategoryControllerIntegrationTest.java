package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.CategoryResponse;
import at.ymeri.my.finance.application.data.CategoryType;
import at.ymeri.my.finance.application.data.CreateCategoryRequest;
import at.ymeri.my.finance.application.data.UpdateCategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class CategoryControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // --- create ---

    @Test
    void createCategory_validRequest_returns201WithId() {
        CreateCategoryRequest request = new CreateCategoryRequest("Groceries", CategoryType.EXPENSE);

        ResponseEntity<CategoryResponse> response = restTemplate
                .postForEntity("/categories", request, CategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Groceries");
    }

    @Test
    void createCategory_missingName_returns400() {
        CreateCategoryRequest request = new CreateCategoryRequest(null, CategoryType.EXPENSE);

        ResponseEntity<String> response = restTemplate
                .postForEntity("/categories", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCategory_missingType_returns400() {
        CreateCategoryRequest request = new CreateCategoryRequest("Transport", null);

        ResponseEntity<String> response = restTemplate
                .postForEntity("/categories", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCategory_duplicateName_returns409() {
        CreateCategoryRequest request = new CreateCategoryRequest("Salary", CategoryType.INCOME);
        restTemplate.postForEntity("/categories", request, CategoryResponse.class);

        ResponseEntity<String> duplicate = restTemplate
                .postForEntity("/categories", request, String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- get ---

    @Test
    void getCategoryById_exists_returns200() {
        CreateCategoryRequest create = new CreateCategoryRequest("Entertainment", CategoryType.EXPENSE);
        CategoryResponse created = restTemplate
                .postForEntity("/categories", create, CategoryResponse.class).getBody();

        ResponseEntity<CategoryResponse> response = restTemplate
                .getForEntity("/categories/" + created.getId(), CategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Entertainment");
    }

    @Test
    void getCategoryById_notFound_returns404() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("/categories/00000000-0000-0000-0000-000000000000", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- update ---

    @Test
    void updateCategory_validRequest_returns200WithUpdatedData() {
        CreateCategoryRequest create = new CreateCategoryRequest("OldName", CategoryType.EXPENSE);
        CategoryResponse created = restTemplate
                .postForEntity("/categories", create, CategoryResponse.class).getBody();

        UpdateCategoryRequest update = new UpdateCategoryRequest("NewName", CategoryType.BOTH);
        ResponseEntity<CategoryResponse> response = restTemplate.exchange(
                "/categories/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                CategoryResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("NewName");
    }

    @Test
    void updateCategory_notFound_returns404() {
        UpdateCategoryRequest update = new UpdateCategoryRequest("X", CategoryType.EXPENSE);
        ResponseEntity<String> response = restTemplate.exchange(
                "/categories/00000000-0000-0000-0000-000000000000",
                HttpMethod.PUT,
                new HttpEntity<>(update),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- delete ---

    @Test
    void deleteCategory_notReferenced_returns204() {
        CreateCategoryRequest create = new CreateCategoryRequest("ToDelete", CategoryType.EXPENSE);
        CategoryResponse created = restTemplate
                .postForEntity("/categories", create, CategoryResponse.class).getBody();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/categories/" + created.getId(),
                HttpMethod.DELETE,
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void deleteCategory_notFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/categories/00000000-0000-0000-0000-000000000000",
                HttpMethod.DELETE,
                null,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
