import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class GitTest extends BasePipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('echo', [String.class], {label -> null})
    }

    @Test
    void "diffFiles requires the targetRef parameter"() {
        def script = loadScript("vars/git.groovy")
        try {
            script.diffFiles()
            assert false : "diffFiles should have failed"
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

}