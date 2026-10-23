package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VersionsHiddenTest {

    @Test
    void patchNumbers() {
        assertTrue(Versions.compare("1.2.3", "1.2.10") < 0);
    }

    @Test
    void majorNumbers() {
        assertTrue(Versions.compare("2.0", "10.0") < 0);
    }

    @Test
    void equalVersions() {
        assertEquals(0, Versions.compare("3.4.5", "3.4.5"));
    }

    @Test
    void missingPartsAreZero() {
        assertEquals(0, Versions.compare("1.0", "1.0.0"));
    }
}
