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
            assert false : 'internal_gitlabRequest should have failed'
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
            assert false : 'internal_gitlabRequest should have failed'
        } catch (AssertionError err) {
            println("[INFO] Caught expected AssertionError: ${err}")
        }
    }

    @Test
    void 'internal_getMergeRequest requires a merge request ID'() {
        def script = loadScript('vars/gitlab.groovy')
        try {
            script.internal_getMergeRequest(projectId: '5')
            assert false : 'internal_getMergeRequest should have failed'
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
}
