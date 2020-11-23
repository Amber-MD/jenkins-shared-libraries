import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class TerraformTest extends BasePipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('error', [String.class], {String message ->
            throw new RuntimeException(message)
        })
    }

    @Test
    void 'verify plan defaults'() {
        def script = loadScript('vars/terraform.groovy')
        script.metaClass.terraformCommand = {String command, String args, boolean errorOnFailure ->
            assert command == 'plan'
            assert args.trim() == '-out terraform.plan'
            assert errorOnFailure
            return true
        }
        assert script.plan()
    }

    @Test
    void 'verify custom args and plan output support'() {
        def script = loadScript('vars/terraform.groovy')
        script.metaClass.terraformCommand = {String command, String args, boolean errorOnFailure ->
            assert command == 'plan'
            assert args.trim() == '-some-arg foo -other-arg bar -out baloney.plan'
            assert errorOnFailure
            return true
        }
        assert script.plan(fileName: 'baloney.plan', args: '-some-arg foo -other-arg bar')
    }

    @Test
    void 'verify custom args with apply'() {
        def script = loadScript('vars/terraform.groovy')
        script.metaClass.terraformCommand = {String command, String args, boolean errorOnFailure ->
            assert command == 'apply'
            assert args.trim() == '-some-arg foo -other-arg bar -auto-approve'
            assert errorOnFailure
            return true
        }
        assert script.apply(args: '-some-arg foo -other-arg bar')
    }

    @Test
    void 'verify apply with log output file'() {
        def script = loadScript('vars/terraform.groovy')
        script.metaClass.terraformCommand = {String command, String args, boolean errorOnFailure ->
            assert command == 'apply'
            assert args.trim() == '-auto-approve | tee terraform.log'
            assert errorOnFailure
            return true
        }
        assert script.apply(fileName: 'terraform.log')
    }
}
