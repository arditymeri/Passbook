package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.controller.bill.BillGetController;
import at.ymeri.my.finance.domain.api.GetBillService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@SpringBootTest(classes = {MyFinanceApplication.class})
@ContextConfiguration(classes = {TestConfig.class, TestDataSourceConfig.class})
@Disabled
public class BillGetControllerIntegrationTest {

    @Autowired
    private BillGetController billGetController;

/*    @MockitoBean
    private GetBillService getBillService;*/

    private static final DockerComposeContainer<?> environment =
            new DockerComposeContainer<>(new File("src/test/resources/docker-compose.yaml"))
                    .withExposedService("kafka", 9092, Wait.forListeningPort()) // Expose Kafka service
                    .withExposedService("zookeeper", 2181, Wait.forListeningPort()); // Expose Zookeeper service

    static {
        environment.start();
    }


    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        String kafkaBootstrapServers = environment.getServiceHost("kafka", 9092) + ":" + environment.getServicePort("kafka", 9092);
        System.out.println("Kafka Bootstrap Servers: " + kafkaBootstrapServers); // Add logging
        registry.add("spring.kafka.bootstrap-servers", () -> kafkaBootstrapServers);
    }

    //private static KafkaContainer kafkaContainer;

/*    @BeforeAll
    static void startKafka() {
        DockerImageName myImage = DockerImageName.parse("confluentinc/cp-kafka:7.8.0")
                        .asCompatibleSubstituteFor("apache/kafka");
        kafkaContainer = new KafkaContainer(myImage);

        kafkaContainer.start();

        String logs = kafkaContainer.getLogs();
        System.out.println("Kafka logs:");
        System.out.println(logs);
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }*/

    @Test
    public void contextLoads() {
        assertThat(billGetController).isNotNull();
    }

    // Add more tests here
}
