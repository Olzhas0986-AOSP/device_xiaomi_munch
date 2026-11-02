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
        if (ProfileUtils.contains("a=com.foo.game,", "foo.game")) {
            throw new AssertionError("Partial package name matched");
        }
        if (!ProfileUtils.contains("a=com.foo.game,foo.game,", "foo.game")) {
            throw new AssertionError("Exact package name did not match");
        }
        equal("a=com.foo.game,:b=", ProfileUtils.removePackage(
                "a=com.foo.game,foo.game,:b=foo.game,", "foo.game"));
        if (!java.util.Arrays.equals(new int[] {1, 2, 0, 3},
                ProfileUtils.parseTouchProfile("1,2,bad,99"))) {
            throw new AssertionError("Malformed touch profile was not repaired");
        }
        if (!java.util.Arrays.equals(new int[4], ProfileUtils.parseTouchProfile(null))) {
            throw new AssertionError("Missing touch profile was not defaulted");
        }
        if (ProfileUtils.boundedInt("bad", 7000, 0, 60000) != 7000
                || ProfileUtils.boundedInt("-5", 1, 1, 10) != 1
                || ProfileUtils.boundedInt("999", 1, 1, 10) != 10) {
            throw new AssertionError("Invalid AutoHBM numeric preference");
        }
        System.out.println("ProfileUtils tests passed");
    }
}
