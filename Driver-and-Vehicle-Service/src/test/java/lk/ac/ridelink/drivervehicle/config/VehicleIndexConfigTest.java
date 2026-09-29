package lk.ac.ridelink.drivervehicle.config;

import java.util.List;
import lk.ac.ridelink.drivervehicle.vehicle.document.Vehicle;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VehicleIndexConfigTest {
    @Test
    void createsNamedIndexesBeforeLifecycleStart() {
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations indexes = mock(IndexOperations.class);
        SmartLifecycle lifecycle = mock(SmartLifecycle.class);
        when(template.indexOps(Vehicle.class)).thenReturn(indexes);
        when(lifecycle.isAutoStartup()).thenReturn(true);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MongoTemplate.class, () -> template);
            context.registerBean(SmartLifecycle.class, () -> lifecycle);
            context.register(VehicleIndexConfig.class);
            context.refresh();
            var definitions = ArgumentCaptor.forClass(IndexDefinition.class);
            var order = inOrder(indexes, lifecycle);
            order.verify(indexes, times(5)).createIndex(definitions.capture());
            order.verify(lifecycle).start();
            var actual = definitions.getAllValues();
            List<Document> expected = List.of(
                    new Document("registrationNumber", 1),
                    new Document("createdAt", -1).append("_id", 1),
                    new Document("driverId", 1).append("createdAt", -1).append("_id", 1),
                    new Document("status", 1).append("createdAt", -1).append("_id", 1),
                    new Document("driverId", 1).append("status", 1).append("createdAt", -1).append("_id", 1));
            String[] names = {"vehicle_registration_unique", "vehicle_created", "vehicle_driver_created",
                    "vehicle_status_created", "vehicle_driver_status_created"};
            for (int i = 0; i < expected.size(); i++) {
                assertThat(actual.get(i).getIndexKeys().entrySet()).containsExactlyElementsOf(expected.get(i).entrySet());
                assertThat(actual.get(i).getIndexOptions()).containsEntry("name", names[i]);
            }
            assertThat(actual.get(0).getIndexOptions()).containsEntry("unique", true);
            actual.subList(1, actual.size()).forEach(index ->
                    assertThat(index.getIndexOptions()).doesNotContainEntry("unique", true));
        }
    }

    @Test
    void indexFailurePreventsLifecycleStart() {
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations indexes = mock(IndexOperations.class);
        SmartLifecycle lifecycle = mock(SmartLifecycle.class);
        when(template.indexOps(Vehicle.class)).thenReturn(indexes);
        when(indexes.createIndex(any())).thenThrow(new DataAccessResourceFailureException("Index creation failed"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MongoTemplate.class, () -> template);
            context.registerBean(SmartLifecycle.class, () -> lifecycle);
            context.register(VehicleIndexConfig.class);
            assertThatThrownBy(context::refresh).isInstanceOf(DataAccessResourceFailureException.class);
            verify(lifecycle, never()).start();
        }
    }
}
