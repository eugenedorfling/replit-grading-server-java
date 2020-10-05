import io.javalin.Javalin;
import io.javalin.core.util.FileUtil;
import io.javalin.http.UploadedFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

  public static void main(String[] args) {
    Javalin app = Javalin.create().start(8080);
    app.get("/", ctx -> {
         ctx.contentType("text/html");
         ctx.result(Main.class.getResourceAsStream("index.html"));
       })
       .post("/", ctx -> {
         UploadedFile upload = ctx.uploadedFile("codefile");
         if (upload == null)
           throw new IllegalArgumentException("No file was uploaded");

         if (upload.getSize() > 10_000)
           throw new IllegalArgumentException("Uploaded file is too large");

         String filename = upload.getFilename();
         if (!filename.endsWith(".java"))
           throw new IllegalArgumentException("Uploaded file is not a Java source file");

         try {
           Path temp = Files.createTempDirectory("autograder_");
           System.err.println(temp.toString());

           copy(upload, temp.toString());
           String compileOutput = compile(temp.toString());
           String testOutput = test(temp.toString());

           ctx.contentType("text/plain");
           ctx.result(compileOutput + "\n\n" + testOutput);

         } catch (IOException ex) {
           throw new IllegalStateException("Unable to process submission file", ex);
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
  private static void copy(UploadedFile upload, String directory) {
    System.err.println("Copying in " + directory);

    // copy the submission to the work directory
    String submission = Paths.get(directory, upload.getFilename()).toString();
    FileUtil.streamToFile(upload.getContent(), submission);

    // copy the test to the work directory
    String test = Paths.get(directory, "ExampleTest.java").toString();
    FileUtil.streamToFile(resource("META-INF/src/ExampleTest.java"), test);

    // copy the dependencies to the work directory
    InputStream in = resource("META-INF/lib");
    BufferedReader br = new BufferedReader(new InputStreamReader(in));
    String lib;
    try {
      while ((lib = br.readLine()) != null) {
        FileUtil.streamToFile(resource("META-INF/lib/" + lib), Paths.get(directory, lib).toString());
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to compile submission", ex);
    }
  }

  private static String compile(String directory) {
    System.err.println("Compiling in " + directory);

    try {
      ProcessBuilder pb = new ProcessBuilder();
      pb.command("javac", "-cp", ".;*", "*.java");
//      pb.command("javac", "-cp", ".:*", "Example.java", "ExampleTest.java");
      pb.directory(new File(directory));
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
      process.waitFor();
      return result;

    } catch (IOException ex) {
      throw new IllegalStateException("Unable to compile submission", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted");
      return null;
    }
  }

  private static String test(String directory) {
    System.err.println("Testing in " + directory);

    try {
      ProcessBuilder pb = new ProcessBuilder();
      pb.command("java",
          "-jar", "junit-platform-console-standalone-1.7.0.jar",
          "-cp", ".",
          "-c", "ExampleTest",
          "--disable-ansi-colors", "--disable-banner",
          "--details=summary", "--details-theme=ascii"
      );
      pb.directory(new File(directory));
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
      process.waitFor();
      return result;

    } catch (IOException ex) {
      throw new IllegalStateException("Unable to compile submission", ex);
    }
    catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted");
      return null;
    }
  }
}
