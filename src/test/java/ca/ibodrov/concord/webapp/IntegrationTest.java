package ca.ibodrov.concord.webapp;

import com.walmartlabs.concord.it.testingserver.TestingConcordServer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

public class IntegrationTest {

    public static void main(String[] args) throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine");
                var server = new TestingConcordServer(db, 8001, Map.of(), List.of(cfg -> new WebappModule()))) {
            db.start();
            server.start();
            Thread.currentThread().join();
        }
    }
}
