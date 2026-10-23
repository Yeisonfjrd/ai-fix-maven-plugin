package bench;

public final class Names {

    private Names() {}

    /** The nickname if the user has one, otherwise first and last name. */
    public static String displayName(User user) {
        if (user.nickname() == null || user.nickname().isBlank()) {
            return user.first() + " " + user.last();
        }
        return user.nickname();
    }
}
