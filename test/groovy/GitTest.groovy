import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class GitTest extends BasePipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('echo', [String.class], {label -> println(label)})
        helper.registerAllowedMethod('sshagent', [List.class, Closure.class], {creds, closure ->
            closure.call()
        })
    }

    @Test
    void 'diffFiles requires the targetRef parameter'() {
        def script = loadScript('vars/git.groovy')
        try {
            script.diffFiles()
            throw new RuntimeException('should not have reached here')
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
        assert fileList == ['file1', 'file2', 'file3'] : 'Unexpected return value for diffFiles'
        String diffCommand = shCalls[1].args[0].script.toString()
        assert diffCommand.contains('git diff --name-only some-ref...HEAD') : 'Bad diff command'
    }

    @Test
    void 'diffFiles fetches the remote when requested with the given gitCredentials'() {
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
            gitCredentialsId: 'some-creds',
            sourceRef: 'other-ref',
            allChanges: true,
        )
        // Do our assertions
        def shCalls = helper.callStack.findAll { call -> call.methodName == 'sh' }
        def sshAgentCalls = helper.callStack.findAll { call -> call.methodName == 'sshagent' }
        assert shCalls.size() == 2 : 'Should have made 2 sh calls'
        assert sshAgentCalls.size() == 1 : 'Should not have called sshagent'
        assert fileList == ['file1', 'file2', 'file3'] : 'Unexpected return value for diffFiles'
        assert sshAgentCalls[0].args[0] == ['some-creds'] : 'Did not use the right credentials'
        String diffCommand = shCalls[1].args[0].script.toString()
        assert diffCommand.contains('git diff --name-only some-ref..other-ref') : 'Bad diff command'
    }

    @Test
    void 'merge requires currentBranch'() {
        def script = loadScript('vars/git.groovy')
        try {
            script.merge(targetBranch: 'target', gitCredentialsId: 'some-creds')
            throw new RuntimeException('should not have reached here')
        } catch(AssertionError err) {
            println('[INFO] Caught expected AssertionError')
        }
    }

    @Test
    void 'merge requires targetBranch'() {
        def script = loadScript('vars/git.groovy')
        try {
            script.merge(sourceBranch: 'source', gitCredentialsId: 'some-creds')
            throw new RuntimeException('should not have reached here')
        } catch(AssertionError err) {
            println('[INFO] Caught expected AssertionError')
        }
    }

    @Test
    void 'merge requires gitCredentialsId'() {
        def script = loadScript('vars/git.groovy')
        try {
            script.merge(currentBranch: 'source', targetBranch: 'target')
            throw new RuntimeException('should not have reached here')
        } catch(AssertionError err) {
            println('[INFO] Caught expected AssertionError')
        }
    }

    @Test
    void 'merge recognizes if no changes were made'() {
        def script = loadScript('vars/git.groovy')
        helper.registerAllowedMethod('readFile', [String.class], {fname ->
            assert fname == 'merge_output.txt' : 'merge_output.txt should be read'
            return 'Already up to date.'
        })
        helper.registerAllowedMethod('sh', [Map.class], {params ->
            return 0
        })
        Map results = script.merge(currentBranch: 'source', targetBranch: 'target',
                                   gitCredentialsId: 'some-creds')

        def shCalls = helper.callStack.findAll {call -> call.methodName == 'sh' && call.args[0].label == 'push changes'}
        assert shCalls.size() == 0 : 'Should not have pushed'
        assert !results.changesMade : 'Should not have made any changes'
        assert results.succeeded : 'Should have recorded a success'
    }

    @Test
    void 'merge will push made changes if requested'() {
        def script = loadScript('vars/git.groovy')
        helper.registerAllowedMethod('readFile', [String.class], {fname ->
            assert fname == 'merge_output.txt' : 'merge_output.txt should be read'
            return 'Updating 9234235..123434\nFast-forward\n .gitignore | 3+--\n'
        })
        helper.registerAllowedMethod('sh', [Map.class], {params ->
            return 0
        })
        Map results = script.merge(currentBranch: 'source', targetBranch: 'target',
                                   gitCredentialsId: 'some-creds')

        def shCalls = helper.callStack.findAll {call -> call.methodName == 'sh' && call.args[0].label == 'push changes'}
        def sshAgentCalls = helper.callStack.findAll {call -> call.methodName == 'sshagent'}
        assert sshAgentCalls.size() == 2: 'Should have separate sshagent blocks for fetch and push'
        assert shCalls.size() == 1 : 'Should have pushed'
        assert results.changesMade : 'Should have made changes'
        assert results.succeeded : 'Should have recorded a success'
    }

    @Test
    void 'merge will not push made changes if not requested'() {
        def script = loadScript('vars/git.groovy')
        helper.registerAllowedMethod('readFile', [String.class], {fname ->
            assert fname == 'merge_output.txt' : 'merge_output.txt should be read'
            return 'Updating 9234235..123434\nFast-forward\n .gitignore | 3+--\n'
        })
        helper.registerAllowedMethod('sh', [Map.class], {params ->
            return 0
        })
        Map results = script.merge(currentBranch: 'source', targetBranch: 'target',
                                   gitCredentialsId: 'some-creds', push: false)

        def shCalls = helper.callStack.findAll {call -> call.methodName == 'sh' && call.args[0].label == 'push changes'}
        def sshAgentCalls = helper.callStack.findAll {call -> call.methodName == 'sshagent'}
        assert sshAgentCalls.size() == 1: 'Should have sshagent block only for fetch'
        assert shCalls.size() == 0 : 'Should not have pushed'
        assert results.changesMade : 'Should have made changes'
        assert results.succeeded : 'Should have recorded a success'
    }
}
