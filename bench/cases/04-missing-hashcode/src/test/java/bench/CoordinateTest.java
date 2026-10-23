package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class CoordinateTest {

    @Test
    void equalCoordinatesCountOnceInASet() {
        Set<Coordinate> visited = new HashSet<>();
        visited.add(new Coordinate(2, 3));
        visited.add(new Coordinate(2, 3));
        assertEquals(1, visited.size());
    }
}
