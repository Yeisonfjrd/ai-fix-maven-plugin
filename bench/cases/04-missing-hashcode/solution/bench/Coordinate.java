package bench;

public final class Coordinate {

    private final int x;
    private final int y;

    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Coordinate c && c.x == x && c.y == y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
