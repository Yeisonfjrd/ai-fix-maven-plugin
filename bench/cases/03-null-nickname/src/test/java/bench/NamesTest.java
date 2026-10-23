package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void withoutNicknameUsesTheFullName() {
        assertEquals("Ana Gómez", Names.displayName(new User("Ana", "Gómez", null)));
    }
}
