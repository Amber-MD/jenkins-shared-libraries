import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class GitHubTest extends BasePipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('echo', [String.class], {label -> println(label)})
        helper.registerAllowedMethod('sshagent', [List.class, Closure.class], {creds, closure ->
            closure.call()
        })
        binding.setVariable('env', [:])
    }

    void setPullRequest() {
        binding.setVariable('env', ['CHANGE_ID': '1'])
        binding.setVariable('pullRequest', [
            'files' : [['filename': 'path/to/file1'],
                       ['filename': 'path/to/file2']]
        ])
    }

    @Test
    void 'fileChangedIn requires path or paths'() {
        def script = loadScript('vars/github.groovy')
        try {
            script.fileChangedIn()
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'fileChangedIn for pull request'() {
        def script = loadScript('vars/github.groovy')
        setPullRequest()
        script.metaClass.filesChangedSinceLastSuccessfulBuild = {
            assert false : 'Should not look through old builds'
        }
        doAsserts(script)
    }

    @Test
    void 'fileChangedIn will look through old builds for change sets'() {
        def script = loadScript('vars/github.groovy')
        script.metaClass.filesChangedSinceLastSuccessfulBuild = {
            return ['changedFiles': ['path/to/file1', 'path/to/file2'], 'foundSuccessfulBuild': true]
        }
        doAsserts(script)
    }

    @Test
    void 'fileChangedIn returns true if no old successful build'() {
        def script = loadScript('vars/github.groovy')
        script.metaClass.filesChangedSinceLastSuccessfulBuild = {
            return ['changedFiles': ['path/to/file1', 'path/to/file2'], 'foundSuccessfulBuild': false]
        }

        assert script.fileChangedIn(path: 'path/to/') : 'Should always return true'
        assert script.fileChangedIn(path: 'bad/path') : 'Should always return true'
    }

    void doAsserts(def script) {
        assert script.fileChangedIn(path: 'path/to/') : 'Should have found a file in path/to/'
        assert script.fileChangedIn(path: 'path/to/file2') : 'Should have found path/to/file2'
        assert !script.fileChangedIn(path: 'path/to/file3') : 'Should not have found path/to/file3'
        assert !script.fileChangedIn(paths: ['bad/path', 'bad/path2']) : 'Should not have found bad/path*'
        assert script.fileChangedIn(paths: ['bad/path', 'bad/path2'], path: 'path/to/') : 'Should have matched path'
        assert script.fileChangedIn(paths: ['bad/path/', 'path/to/'], path: 'bad/path2') : 'Should have matched 1 path'
    }

}
