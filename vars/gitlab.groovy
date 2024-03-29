/**
 * This package contains functionality for interacting with the REST API of
 * GitLab. This uses the v4 version of the API.
 *
 * The authentication mode assumed here uses user tokens. See more at
 * https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html
 *
 * gitlabCredentialsId should always be a "secret text" type of credentials
 */
import groovy.transform.Field
import groovy.json.JsonOutput

// This should be set to the address of the GitLab server you wish to use
@Field String GITLAB_SERVER = 'https://gitlab.ambermd.org'

// This can be overridden on a per-function basis
@Field String defaultCredentialsId = 'amber-gitlab-automaton-token'

/**
 * Gets the full set of details about a given merge request
 *
 * :param projectId: The ID of the project. Required
 * :param mergeRequestId: This is the merge request Iid (not Id), which is the
 *                        project-specific ID, not the globally-unique ID. Required
 * :param gitlabCredentialsId: The credentials ID storing the GitLab access token to use.
 *                             Default is 'amber-gitlab-automaton-token'
 * :returns: the user information of the person that raised the merge request
 */
Map mergeRequestAuthor(Map params = [:]) {
    Map mergeRequest = internal_getMergeRequest(params)
    return mergeRequest.author
}

/**
 * Gets the state of the merge request
 *
 * :param projectId: The ID of the project. Required
 * :param mergeRequestId: This is the merge request Iid (not Id), which is the
 *                        project-specific ID, not the globally-unique ID. Required
 * :param gitlabCredentialsId: The credentials ID storing the GitLab access token to use.
 *                             Default is 'amber-gitlab-automaton-token'
 * :returns: the status of the merge request as a string
 */
String mergeRequestState(Map params = [:]) {
    Map mergeRequest = internal_getMergeRequest(params)
    return mergeRequest.state
}

/**
 * Posts a comment on a merge request
 *
 * :param message: The message to post to the merge request discussion. Required
 * :param projectId: The ID of the project. Required.
 * :param mergeRequestId: This is the merge request Iid (not Id), which is the
 *                        project-specific ID, not the globally-unique ID. Required
 * :param gitlabCredentialsId: The credentials ID storing the GitLab access token to use.
 *                             Default is 'amber-gitlab-automaton-token'
 * :param files: A list of file names to upload. The message must have the tokens FILE1,
                 FILE2, FILE3, ..., FILEN for i = 1 to the size of the list of files. These
                 tokens will be replaced with the markdown links to the uploaded attachments.
 * :returns: the REST API response
 */
Map mergeRequestComment(Map params = [:]) {
    assert params.message : 'message is required'
    assert params.projectId : 'projectId is required'
    assert params.mergeRequestId : 'mergeRequestId is required'

    String message = params.message
    String credentialsId = params.gitlabCredentialsId ?: defaultCredentialsId
    String[] files = params.files ?: []

    if (files.size() > 0) {
        for (int i = 0; i < files.size(); i++) {
            String token = "FILE${i+1}"
            assert message.contains(token) : "${i+1}+ files were uploaded, but ${token} placeholder was not in message"
            // Check that all files exist before we try uploading any
            assert fileExists(files[i]) : "${files[i]} does not exist; cannot upload"
        }
        // If we got here, then we have all the necessary tokens and files all exist. Go ahead and upload each one
        for (int i = 0; i < files.size(); i++) {
            Map response = internal_gitlabUploadAttachment(
                projectId: params.projectId,
                fileName: files[i],
                credentialsId: credentialsId,
            )
            echo "INFO: Uploaded new attachment ${files[i]}"
            message = message.replaceAll("FILE${i+1}", response.markdown)
        }
    }

    return internal_gitlabRequest(
        uri: "api/v4/projects/${params.projectId}/merge_requests/${params.mergeRequestId}/discussions",
        requestBody: JsonOutput.toJson(['body': message]),
        credentialsId: credentialsId,
        httpMode: 'POST',
    )
}

/**
 * Internal method for getting a merge request from the REST API
 *
 * :param projectId: The ID of the project. Required
 * :param mergeRequestId: This is the merge request Iid (not Id), which is the
 *                        project-specific ID, not the globally-unique ID. Required
 * :param gitlabCredentialsId: The credentials ID storing the GitLab access token to use.
 *                             Default is 'amber-gitlab-automaton-token'
 * :returns: Map that is the deserialized response from the GitLab API
 */
Map internal_getMergeRequest(Map params = [:]) {
    assert params.projectId : 'projectId is required'
    assert params.mergeRequestId : 'mergeRequestId is required'

    String projectId = params.projectId
    String mergeRequestId = params.mergeRequestId

    return internal_gitlabRequest(
        uri: "api/v4/projects/${projectId}/merge_requests/${mergeRequestId}",
        credentialsId: params.gitlabCredentialsId ?: defaultCredentialsId,
    )
}

/// Utility to send arbitrary gitlab REST requests with auth headers
Map internal_gitlabRequest(Map params = [:]) {
    assert params.uri : 'uri is required'

    String uri = params.uri
    String credentialsId = params.credentialsId ?: defaultCredentialsId
    String validResponseCodes = params.validResponseCodes ?: '100:399'
    String httpMode = params.httpMode ?: 'GET'

    Map response

    withCredentials([string(credentialsId: credentialsId, variable: 'GITLAB_TOKEN')]) {
        def resp = httpRequest(
            url: "${GITLAB_SERVER}/${uri}",
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            httpMode: httpMode,
            customHeaders: [
                [name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}", maskValue: true]
            ],
            validResponseCodes: validResponseCodes,
            requestBody: params.requestBody,
        )
        response = readJSON(text: resp.getContent())
    }
    return response
}

/// Utility to upload attachments to a project
Map internal_gitlabUploadAttachment(Map params = [:]) {
    assert params.projectId : 'projectId is required'
    assert params.fileName : 'fileName is required'
    assert fileExists(params.fileName) : "${params.fileName} must exist"

    String validResponseCodes = params.validResponseCodes ?: '100:399'
    String url = "${GITLAB_SERVER}/api/v4/projects/${params.projectId}/uploads"
    String credentialsId = params.credentialsId ?: defaultCredentialsId
    String fileName = params.fileName

    Map response

    withCredentials([string(credentialsId: credentialsId, variable: 'GITLAB_TOKEN')]) {
        def resp = httpRequest(
            url: url,
            httpMode: 'POST',
            wrapAsMultipart: true,
            multipartName: 'file',
            customHeaders: [
                [name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}", maskValue: true]
            ],
            uploadFile: fileName
        )
        response = readJSON(text: resp.getContent())
    }
    return response
}
