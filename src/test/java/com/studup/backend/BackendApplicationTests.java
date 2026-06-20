package com.studup.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Ce test nécessite Redis et MinIO disponibles — activé uniquement avec Testcontainers (US-049)
@Disabled("Nécessite Redis + MinIO — voir US-049 Testcontainers")
@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
