import java.nio.file.*;
import org.lineageos.settings.utils.FileUtils;
public class FileUtilsTest {
 public static void main(String[] args) throws Exception {
  Path file=Files.createTempFile("parts-file-utils-", ".txt");
  try {
   if(!FileUtils.writeLine(file.toString(),"123") || !"123".equals(FileUtils.readOneLine(file.toString()))) throw new AssertionError("Normal write failed");
   if(Files.exists(Paths.get("/dev/full")) && FileUtils.writeLine("/dev/full","123")) throw new AssertionError("Flush failure reported as success");
  } finally { Files.delete(file); }
  System.out.println("FileUtils real-file and flush-error tests passed");
 }
}
