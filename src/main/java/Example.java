// JS1337A
// Ensure the above line contains exactly your student number.
//
// You can test your code by pressing the `Run` button.
// Once you are happy with it, change the `ReadyForSubmission` line
// to say `YES` and press the `Run` button again to submit
// -----------------------------------------------------------------
// ReadyForSubmission=NO
public class Example {
  // Add a and b and return the sum
  public int add(int a, int b){
    return a+b;
  }

  // Subtract b from a and return the result
  public int subtract(int a, int b){
    return a-b;
  }

  private void test() {
    System.out.printf("add(1, 2) returns %s\n", add(1, 2));
    System.out.printf("subtract(5, 2) returns %s\n", subtract(5, 2));
  }

  public static void main(String[] args) {
    new Example().test();
  }
}