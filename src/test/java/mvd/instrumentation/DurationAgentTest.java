package mvd.instrumentation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class DurationAgentTest {

    @Test
    public void firstTest() {
        assertEquals(2, 1 + 1);
    }
}
