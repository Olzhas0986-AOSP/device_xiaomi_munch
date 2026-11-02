import org.lineageos.settings.utils.ProfileUtils;

public class ProfileUtilsTest {
    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    public static void main(String[] args) {
        String[] keys = {"a=", "b=", "c=", "d=", "e=", "f="};
        equal("a=:b=:c=:d=:e=:f=", ProfileUtils.normalize(null, keys));
        equal("a=:b=:c=:d=:e=:f=", ProfileUtils.normalize("", keys));
        equal("a=com.game,:b=:c=:d=:e=:f=",
                ProfileUtils.normalize("a=com.game,:b=:c=:d=:e=", keys));
        equal("a=:b=:c=:d=:e=:f=",
                ProfileUtils.normalize("garbage:b=:c=:d=:e=:f=", keys));
        System.out.println("ProfileUtils tests passed");
    }
}
