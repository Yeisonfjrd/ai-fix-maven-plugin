package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NamesHiddenTest {

    @Test
    void nicknameWins() {
        assertEquals("Anita", Names.displayName(new User("Ana", "Gómez", "Anita")));
    }

    @Test
    void blankNicknameUsesTheFullName() {
        assertEquals("Bruno Díaz", Names.displayName(new User("Bruno", "Díaz", "   ")));
    }

    @Test
    void anotherUserWithoutNickname() {
        assertEquals("Carla Ruiz", Names.displayName(new User("Carla", "Ruiz", null)));
    }
}
