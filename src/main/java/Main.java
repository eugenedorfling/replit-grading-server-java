import io.javalin.Javalin;
import io.javalin.core.util.FileUtil;
import io.javalin.http.UploadedFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

  // the class name of the of the submission
  private static final String SUBMISSION_CLASS_NAME = "Example";

  // the class name of the test case
  // this _should_ be SUBMISSION_CLASS_NAME + "Test"
  // but at an absolute minimum, it *must* end in "Test"
  private static final String TEST_CLASS_NAME = "ExampleTest";

  // ----------------------------------------------
  // you shouldn't need to edit anything below here
  // ----------------------------------------------

  private static final String SUBMISSION_FILE_NAME = SUBMISSION_CLASS_NAME + ".java";
  private static final String TEST_FILE_NAME = TEST_CLASS_NAME + ".java";

  private static final Pattern TEST_OUTPUT_PATTERN = Pattern.compile(
      "(\\d+) tests successful" +
      ".*" +
      "(\\d+) tests failed",
      Pattern.DOTALL
  );

  private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd MMMMMMMMMM yyyy hh:mma");
  private static String OS = System.getProperty("os.name").toLowerCase();

  public static void main(String[] args) {
    Javalin app = Javalin.create().start(8080);
    app.get("/", ctx -> {
         ctx.contentType("text/html");
         ctx.result(Main.class.getResourceAsStream("index.html"));
       })
       .post("/", ctx -> {
         try {
           UploadedFile upload = ctx.uploadedFile("codefile");
           if (upload == null)
             throw new IllegalArgumentException("No file was uploaded");

           if (upload.getSize() > 10_000)
             throw new IllegalArgumentException("Uploaded file is too large");

           String filename = upload.getFilename();
           if (!SUBMISSION_FILE_NAME.equals(filename))
             throw new IllegalArgumentException("Uploaded file is not a Java source file for class " + SUBMISSION_CLASS_NAME);

           // fixme: slurp the file and figure out the student number, or accept a param?

           String temp = Files.createTempDirectory("autograder_").toString();
           System.err.println(temp);

           copy(upload, temp);
           compile(temp);
           String testOutput = test(temp);
           report(testOutput, temp);

           ctx.contentType("text/plain");
           ctx.result("Your code has been submitted");

         } catch (Exception ex) {
           ex.printStackTrace(System.err);

           ctx.contentType("text/plain");
           ctx.result("Sorry, something went wrong");
         }
       });
  }

  private static InputStream resource(String name) {
    return Main.class.getResourceAsStream(name);
  }

  private static String extractStudentNumber(String codefile) {
    Pattern pattern = Pattern.compile("^//\\s*(\\S+)");
    Matcher matcher = pattern.matcher(codefile);
    if (matcher.find()) {
      return matcher.group(1);
    }

    throw new IllegalArgumentException("No student identifier found");
  }

  // copy the uploaded file and any other files necessary for compilation and testing
  private static void copy(UploadedFile upload, String directory) throws Exception {
    System.err.println("Copying in " + directory);

    // copy the submission to the work directory
    String submission = Paths.get(directory, upload.getFilename()).toString();
    FileUtil.streamToFile(upload.getContent(), submission);

    // copy the test to the work directory
    String test = Paths.get(directory, TEST_FILE_NAME).toString();
    FileUtil.streamToFile(resource("META-INF/src/" + TEST_FILE_NAME), test);

    // copy the dependencies to the work directory
    InputStream in = resource("META-INF/lib");
    BufferedReader br = new BufferedReader(new InputStreamReader(in));
    String lib;
    while ((lib = br.readLine()) != null) {
      FileUtil.streamToFile(resource("META-INF/lib/" + lib), Paths.get(directory, lib).toString());
    }
  }

  private static String compile(String directory) throws Exception {
    System.err.println("Compiling in " + directory);

    ProcessBuilder pb = new ProcessBuilder();
    if (OS.contains("win"))
      pb.command("javac", "-cp", ".;*", "*.java");
    else
      pb.command("javac", "-cp", ".:*", SUBMISSION_FILE_NAME, TEST_FILE_NAME);
    pb.directory(new File(directory));
    pb.redirectErrorStream(true);
    Process process = pb.start();

    String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
    process.waitFor();
    return result;
  }

  private static String test(String directory) throws Exception {
    System.err.println("Testing in " + directory);

    ProcessBuilder pb = new ProcessBuilder();
    pb.command("java",
        "-jar", "junit-platform-console-standalone-1.7.0.jar",
        "-cp", ".",
        "-c", TEST_CLASS_NAME,
        "--disable-ansi-colors", "--disable-banner",
        "--details=summary", "--details-theme=ascii"
    );
    pb.directory(new File(directory));
    pb.redirectErrorStream(true);
    Process process = pb.start();

    String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
    process.waitFor();
    return result;
  }

  private static void report(String testOutput, String workspace) throws Exception {
    Matcher matcher = TEST_OUTPUT_PATTERN.matcher(testOutput);
    if (!matcher.find())
      throw new IllegalStateException("Unable to match test output");

    int passed = Integer.parseInt(matcher.group(1));
    int failed = Integer.parseInt(matcher.group(2));

    int score = (int) ((((double) passed) / ((double) (passed + failed))) * 100);
    System.err.println("score: " + score);

    String code = Files.readString(Paths.get(workspace, SUBMISSION_FILE_NAME));
    String sn = extractStudentNumber(code);

    String date = DATE_FORMAT.format(new Date());
    String template = "### %s submitted at %s\n" +
        "* **Passed:** %d\n" +
        "* **Failed:** %d\n" +
        "* **Score:** %d%%\n";

    String report = String.format(template, sn, date, passed, failed, score);
    System.err.println(report);

    save(sn, report, workspace);
  }

  private static void save(String sn, String report, String workspace) throws Exception {
    // slight sanity check on the directory to create...
    sn = sn.replaceAll("[^a-zA-Z0-9]", "_");

    File dir = null;
    for (int i = 0; i < 10_000; i++) {
      dir = new File(sn + ((i == 0) ? "" : ("_" + i)));
      if (!dir.exists())
        break;
    }
    if (dir.exists())
      throw new IllegalStateException("Can't create directory name to store submission");

    if (!dir.mkdir())
      throw new IllegalStateException("Can't create directory to store submission");

    Files.copy(Paths.get(workspace, SUBMISSION_FILE_NAME), Paths.get(dir.getAbsolutePath(), SUBMISSION_FILE_NAME));
    Files.writeString(Paths.get(dir.getAbsolutePath(), "report.md"), report, StandardCharsets.US_ASCII);
  }
}
