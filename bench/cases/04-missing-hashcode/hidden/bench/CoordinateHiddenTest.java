package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class CoordinateHiddenTest {

    @Test
    void mapLookupWithANewInstance() {
        Map<Coordinate, String> board = new HashMap<>();
        board.put(new Coordinate(-1, 7), "tower");
        assertEquals("tower", board.get(new Coordinate(-1, 7)));
    }

    @Test
    void differentCoordinatesStayDifferent() {
        Set<Coordinate> set = new HashSet<>(Set.of(new Coordinate(1, 2), new Coordinate(2, 1)));
        assertEquals(2, set.size());
        assertTrue(set.contains(new Coordinate(2, 1)));
    }
}
