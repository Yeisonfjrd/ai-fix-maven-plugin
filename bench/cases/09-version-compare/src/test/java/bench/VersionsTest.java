package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VersionsTest {

    @Test
    void tenIsNewerThanNine() {
        assertTrue(Versions.compare("1.10", "1.9") > 0);
    }
}
