package dev.itobey.adapter.api.fddb.exporter.testutil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

/**
 * Utility class for loading test data from JSON files in test resources.
 * Provides convenient methods to deserialize JSON test fixtures into domain objects.
 */
@UtilityClass
public class TestDataLoader {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Loads a JSON file from the classpath and deserializes it into the specified type.
     *
     * @param resourcePath the path to the JSON file relative to test resources (e.g., "testdata/download-service/input.json")
     * @param type         the class of the target type
     * @param <T>          the target type
     * @return the deserialized object
     */
    @SneakyThrows
    public static <T> T loadFromJson(String resourcePath, Class<T> type) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            return OBJECT_MAPPER.readValue(inputStream, type);
        }
    }

    /**
     * Loads a JSON file from the classpath and deserializes it into the specified type reference.
     * Use this method for generic types like List<MyClass>.
     *
     * @param resourcePath the path to the JSON file relative to test resources
     * @param typeRef      the type reference for the target type
     * @param <T>          the target type
     * @return the deserialized object
     */
    @SneakyThrows
    public static <T> T loadFromJson(String resourcePath, TypeReference<T> typeRef) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            return OBJECT_MAPPER.readValue(inputStream, typeRef);
        }
    }

    /**
     * Loads a list of objects from a JSON array file.
     *
     * @param resourcePath the path to the JSON file relative to test resources
     * @param elementType  the class of the list element type
     * @param <T>          the element type
     * @return the list of deserialized objects
     */
    @SneakyThrows
    public static <T> List<T> loadListFromJson(String resourcePath, Class<T> elementType) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            return OBJECT_MAPPER.readValue(inputStream,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        }
    }
}

