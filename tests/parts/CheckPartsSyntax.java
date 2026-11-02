import java.nio.file.*;
import java.util.*;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.*;
import org.eclipse.jdt.core.compiler.CategorizedProblem;

public class CheckPartsSyntax {
    public static void main(String[] args) throws Exception {
        CompilerOptions options = new CompilerOptions();
        options.sourceLevel = options.complianceLevel = ClassFileConstants.JDK17;
        ProblemReporter reporter = new ProblemReporter(
                DefaultErrorHandlingPolicies.proceedWithAllProblems(), options,
                new DefaultProblemFactory(Locale.ROOT));
        int count = 0;
        boolean failed = false;
        for (String path : args) {
            CompilationUnit unit = new CompilationUnit(
                    new String(Files.readAllBytes(Paths.get(path)), "UTF-8").toCharArray(),
                    path, "UTF-8");
            CompilationResult result = new CompilationResult(unit, 0, 1, 100);
            Parser parser = new Parser(reporter, false);
            parser.parse(unit, result);
            if (result.hasErrors()) {
                failed = true;
                for (CategorizedProblem problem : result.getErrors()) {
                    System.err.println(path + ":" + problem.getSourceLineNumber() + ": " + problem.getMessage());
                }
            }
            count++;
        }
        if (failed) System.exit(1);
        System.out.println("Parsed " + count + " Java files successfully");
    }
}
