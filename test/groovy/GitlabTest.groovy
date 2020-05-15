import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import mock.HttpResponse
import groovy.json.JsonSlurper

class GitlabTest extends BasePipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('echo', [String.class], {label -> println(label)})
        helper.registerAllowedMethod('withCredentials', [List.class, Closure.class], {creds, closure ->
            closure.call()
        })
        helper.registerAllowedMethod('readJSON', [Map.class], {content ->
            return (new JsonSlurper()).parseText(content.text)
        })
    }

    @Test
    void 'internal_gitlabRequest requires a uri'() {
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.internal_gitlabRequest()
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'verify internal_gitlabRequest defaults'() {
        helper.registerAllowedMethod('httpRequest', [Map.class], {params ->
            return new HttpResponse('{"some": "json"}', 200)
        })
        binding.setVariable('GITLAB_TOKEN', 'gitlab-api-token')
        def script = loadScript('vars/gitlab.groovy')
        Map resp = script.internal_gitlabRequest(uri: 'api/v4/projects')
        assert resp.some == 'json' : 'Did not properly deserialize expected response'

        List requestCalls = helper.callStack.findAll {call -> call.methodName == 'httpRequest'}
        assert requestCalls.size() == 1 : 'Should have made 1 httpRequest call'
        assert requestCalls[0].args[0].customHeaders.size() == 1 : 'Expected auth header'
        assert requestCalls[0].args[0].customHeaders[0].name == 'Authorization' : 'expected auth header'
        assert requestCalls[0].args[0].customHeaders[0].value == 'Bearer gitlab-api-token' : 'expected default token auth'
        assert requestCalls[0].args[0].httpMode == 'GET' : 'GET should be default method'
        assert requestCalls[0].args[0].validResponseCodes == '100:399' : 'Default valid codes should all be <400'

        List credCalls = helper.callStack.findAll {call -> call.methodName == 'withCredentials'}
        assert credCalls.size() == 1 : 'Should have executed one withCredentials call'
        assert credCalls[0].args[0].size() == 1 : 'Should have only used 1 set of creds'
    }

    @Test
    void 'verify overridden internal_gitlabRequest parameters'() {
        helper.registerAllowedMethod('httpRequest', [Map.class], {params ->
            return new HttpResponse('{"some": "json"}', 200)
        })
        binding.setVariable('GITLAB_TOKEN', 'gitlab-api-token')
        def script = loadScript('vars/gitlab.groovy')
        Map resp = script.internal_gitlabRequest(uri: 'api/v4/projects', httpMode: 'POST',
                                                 validResponseCodes: '100:599')
        assert resp.some == 'json' : 'Did not properly deserialize expected response'

        List requestCalls = helper.callStack.findAll {call -> call.methodName == 'httpRequest'}
        assert requestCalls.size() == 1 : 'Should have made 1 httpRequest call'
        assert requestCalls[0].args[0].customHeaders.size() == 1 : 'Expected auth header'
        assert requestCalls[0].args[0].customHeaders[0].name == 'Authorization' : 'expected auth header'
        assert requestCalls[0].args[0].customHeaders[0].value == 'Bearer gitlab-api-token' : 'expected default token auth'
        assert requestCalls[0].args[0].httpMode == 'POST' : 'POST was specified'
        assert requestCalls[0].args[0].validResponseCodes == '100:599' : 'Should take custom valid codes'

        List credCalls = helper.callStack.findAll {call -> call.methodName == 'withCredentials'}
        assert credCalls.size() == 1 : 'Should have executed one withCredentials call'
        assert credCalls[0].args[0].size() == 1 : 'Should have only used 1 set of creds'
    }

    @Test
    void 'internal_getMergeRequest requires a project ID'() {
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.internal_getMergeRequest(mergeRequestId: '5')
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'internal_getMergeRequest requires a merge request ID'() {
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.internal_getMergeRequest(projectId: '5')
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'internal_getMergeRequest correctly processes response'() {
        def script = loadScript('vars/gitlab.groovy')
        script.metaClass.internal_gitlabRequest = {Map params = [:] ->
            assert params.uri == 'api/v4/projects/5/merge_requests/10'
            return ['foo': 'bar']
        }
        Map resp = script.internal_getMergeRequest(projectId: '5', mergeRequestId: '10')
        assert resp.foo == 'bar' : 'Unexpected response from internal_getMergeRequest'
    }

    @Test
    void 'mergeRequestAuthor correctly returns just the author'() {
        def script = loadScript('vars/gitlab.groovy')
        script.metaClass.internal_getMergeRequest = {Map params ->
            assert params.projectId == '5'
            assert params.mergeRequestId == '10'
            return ['extra': 'parameter', 'author' : ['username': 'foo', 'email': 'bar@email.com']]
        }
        Map resp = script.mergeRequestAuthor(projectId: '5', mergeRequestId: '10')
        assert resp == ['username': 'foo', 'email': 'bar@email.com'] : 'Unexpected response'
    }

    @Test
    void 'mergeRequestComment requires a message'() {
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.mergeRequestComment(projectId: '5', mergeRequestId: '10')
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'mergeRequestComment requires a projectId'() {
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.mergeRequestComment(message: 'some message', mergeRequestId: '10')
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'mergeRequestComment requires a mergeRequestId'() {
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.mergeRequestComment(message: 'some message', projectId: '5')
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'mergeRequestComment with files requires tokens to exist'() {
        def script = loadScript('vars/gitlab.groovy')
        helper.registerAllowedMethod('fileExists', [String.class], {name -> true})
        try {
            script.mergeRequestComment(message: 'some-message', projectId: '5', mergeRequestId: '10',
                                       files: ['file1.png', 'file2.jpg', 'file3.tgz'])
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'mergeRequestComment with files requires all tokens to exist'() {
        def script = loadScript('vars/gitlab.groovy')
        helper.registerAllowedMethod('fileExists', [String.class], {name -> true})
        try {
            script.mergeRequestComment(message: 'some-message FILE1 FILE2', projectId: '5', mergeRequestId: '10',
                                       files: ['file1.png', 'file2.jpg', 'file3.tgz'])
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'mergeRequestComment with files requires files to exist'() {
        def script = loadScript('vars/gitlab.groovy')
        helper.registerAllowedMethod('fileExists', [String.class], {name -> name != 'file3.tgz'})
        try {
            script.mergeRequestComment(message: 'FILE1 FILE2 FILE3', projectId: '5', mergeRequestId: '10',
                                       files: ['file1.png', 'file2.jpg', 'file3.tgz'])
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'mergeRequestComment sends a request to the GitLab API'() {
        def script = loadScript('vars/gitlab.groovy')
        script.metaClass.internal_gitlabRequest = {Map params ->
            return ['body': params.requestBody, 'uri': params.uri, 'credentials': params.credentialsId,
                    'mode': params.httpMode]
        }
        Map resp = script.mergeRequestComment(message: 'some message', projectId: '5',
                                              mergeRequestId: '10')
        assert resp == ['body': '{"body":"some message"}', 'uri': 'api/v4/projects/5/merge_requests/10/discussions',
                        'mode': 'POST', 'credentials': 'amber-gitlab-automaton-token']
    }

    @Test
    void 'mergeRequestComment uploads files and adds markdown to body'() {
        def script = loadScript('vars/gitlab.groovy')
        script.metaClass.internal_gitlabUploadAttachment = {Map params ->
            return ['markdown': "![label](/uploads/some-guid/${params.fileName})",
                    'url': "/uploads/some-guid/${params.fileName}", 'alt': params.fileName]
        }
        script.metaClass.internal_gitlabRequest = {Map params ->
            String assertString = ('{"body":"' +
                '![label](/uploads/some-guid/file1.png) ' +
                '![label](/uploads/some-guid/file2.jpg) ' +
                '![label](/uploads/some-guid/file3.tgz)"}')
            assert params.requestBody == assertString : 'Substitutions did not happen'
            return ['foo': 'bar']
        }
        helper.registerAllowedMethod('fileExists', [String.class], {fileName -> true})

        def resp = script.mergeRequestComment(message: 'FILE1 FILE2 FILE3', projectId: '5', mergeRequestId: '10',
                                              files: ['file1.png', 'file2.jpg', 'file3.tgz'])
        assert resp.foo == 'bar' : 'Did not process the internal response properly'
    }

    @Test
    void 'internal_gitlabUploadAttachment requires a fileName'() {
        helper.registerAllowedMethod('fileExists', [String.class], {fileName -> true})
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.internal_gitlabUploadAttachment(projectId: '5')
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'internal_gitlabUploadAttachment requires a projectId'() {
        helper.registerAllowedMethod('fileExists', [String.class], {fileName -> true})
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.internal_gitlabUploadAttachment(fileName: 'some-file.png')
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'internal_gitlabUploadAttachment requires a file that exists'() {
        helper.registerAllowedMethod('fileExists', [String.class], {fileName -> false})
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.internal_gitlabUploadAttachment(fileName: 'some-file.png', projectId: '5')
            throw new RuntimeException('should not have reached here')
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'verify internal_gitlabUploadAttachment defaults'() {
        helper.registerAllowedMethod('fileExists', [String.class], {fileName ->
            return fileName == 'some-file.png'
        })
        helper.registerAllowedMethod('httpRequest', [Map.class], {params ->
            assert params.wrapAsMultipart
            assert params.multipartName == 'file'
            assert params.httpMode == 'POST'
            assert params.uploadFile == 'some-file.png'
            assert params.url == 'https://gitlab.ambermd.org/api/v4/projects/5/uploads'
            return new HttpResponse('{"some": "json"}', 201)
        })
        binding.setVariable('GITLAB_TOKEN', 'gitlab-api-token')
        def script = loadScript('vars/gitlab.groovy')
        Map resp = script.internal_gitlabUploadAttachment(projectId: '5', fileName: 'some-file.png')
        assert resp.some == 'json' : 'Did not properly deserialize expected response'

        List requestCalls = helper.callStack.findAll {call -> call.methodName == 'httpRequest'}
        assert requestCalls.size() == 1 : 'Should have made 1 httpRequest call'

        List credCalls = helper.callStack.findAll {call -> call.methodName == 'withCredentials'}
        assert credCalls.size() == 1 : 'Should have executed one withCredentials call'
    }
}
