import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class GitTest extends BasePipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('echo', [String.class], {label -> println(label)})
        helper.registerAllowedMethod('sshagent', [String.class, Closure.class], {creds, closure ->
            closure.call()
        })
    }

    @Test
    void 'diffFiles requires the targetRef parameter'() {
        def script = loadScript('vars/git.groovy')
        try {
            script.diffFiles()
            assert false : 'diffFiles should have failed'
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'diffFiles fetches the remote when requested'() {
        def script = loadScript('vars/git.groovy')
        helper.registerAllowedMethod('sh', [Map.class], {params ->
            if (params.returnStdout) {
                if (params.label == 'git diff') {
                    return 'file1\nfile2\nfile3'
                }
                return ''
            }
            return null
        })
        String[] fileList = script.diffFiles(
            targetRef: 'some-ref',
            fetchRemote: 'git@github.com:/Amber-MD/jenkins-shared-libraries',
        )
        // Do our assertions
        def shCalls = helper.callStack.findAll { call -> call.methodName == 'sh' }
        def sshAgentCalls = helper.callStack.findAll { call -> call.methodName == 'sshagent' }
        assert shCalls.size() == 2 : 'Should have made 2 sh calls'
        assert sshAgentCalls.size() == 0 : 'Should not have called sshagent'
    }
}