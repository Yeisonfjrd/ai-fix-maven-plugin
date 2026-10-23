package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SlugTest {

    @Test
    void doubleSpace() {
        assertEquals("hola-mundo", Slug.of("Hola  Mundo"));
    }
}
