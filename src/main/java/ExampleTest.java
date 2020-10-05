import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExampleTest {

  @Test
  void add_two_pos() {
    assertEquals(3, new Example().add(1, 2), "1 + 2");
  }

  @Test
  void add_one_neg_one_pos() {
    assertEquals(-2, new Example().add(-3, 1), "-3 + 1");
  }

  @Test
  void subtract_two_pos() {
    assertEquals(2, new Example().subtract(5, 3), "5 - 3");
  }

  @Test
  void subtract_two_neg() {
    assertEquals(1, new Example().subtract(-2, -3), "-2 - -3");
  }
}
