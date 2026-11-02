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
        System.out.println("ProfileUtils tests passed");
    }
}
