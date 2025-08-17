package util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class NameUtilityTest {
    @Test
    void examples() {
        assertThat(NameUtility.convertNameToInitials("Bruno Mars")).isEqualTo("B.M.");
        assertThat(NameUtility.convertNameToInitials("Dave M Jones")).isEqualTo("D.M.J.");
        assertThat(NameUtility.convertNameToInitials("MichaelSmith")).isEqualTo("M.");
    }
}
