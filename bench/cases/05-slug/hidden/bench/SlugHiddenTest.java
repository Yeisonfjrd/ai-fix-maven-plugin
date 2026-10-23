package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SlugHiddenTest {

    @Test
    void spacesAroundAndInside() {
        assertEquals("java-21", Slug.of("  Java   21 "));
    }

    @Test
    void tabs() {
        assertEquals("uno-dos", Slug.of("uno\tdos"));
    }

    @Test
    void oneWord() {
        assertEquals("simple", Slug.of("Simple"));
    }
}
