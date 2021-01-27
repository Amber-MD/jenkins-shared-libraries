/**
 * Returns true if the path(s) have been changed and false otherwise. Note that for pull requests,
 * the files changed are available in the build information. The credentials and GitHub API will
 * only be used for branch builds where CHANGE_ID is not set.
 *
 * This check does *not* require an allocated executor.
 *
 * :param path: Name of the file or path prefix where changes should be looked for
 * :param paths: A list of file or path prefix names where changes should be looked for
 * :param credentialsId: A set of username/password credentials to access the GitHub API (needed only for private repos)
 * :param githubApiUrl: The API URL for the GitHub instance. Default is https://api.github.com
 */
import org.eclipse.jgit.transport.RemoteConfig
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import hudson.plugins.git.util.BuildData

boolean fileChangedIn(Map params = [:]) {
    String path = params.path ?: ''
    def paths = params.paths ?: []

    if (paths == [] && path == '') {
        assert path : 'ERROR: Must pass either path or paths'
    } else if (path) {
        paths << path
    }

    def changedFiles = []
    if (env.CHANGE_ID) {
        pullRequest.files.each { changedFiles << it.filename }
    } else {
        echo "INFO: Not a pull request; looking for changes since the last successful build"
        try {
            Map result = filesChangedSinceLastSuccessfulBuild(params.githubApiUrl ?: "https://api.github.com", params.credentialsId ?: "")
        } catch {
            echo "ERROR: Failed fetching change list. Assume something changed"
            return true
        }
        if (!result.foundSuccessfulBuild) {
            echo "WARNING: Did not find a prior successful build. Forcibly saying yes"
            return true
        }
        if (result.changedFiles.size() == 0) {
            echo "WARNING: No changed files found. Forcibly saying yes"
            return true
        }
        changedFiles = result.changedFiles
    }

    for (String changedFile : changedFiles) {
        boolean anyMatch = paths.any { changedFile.startsWith(it) }
        if (anyMatch || path == "." || paths.contains(".")) {
            echo "INFO: Changed file ${changedFile} matches"
            return true
        }
    }
    echo "INFO: No changed file matches any given path"
    return false
}

// Internal method for finding list of files that have changed since the last build
Map filesChangedSinceLastSuccessfulBuild(String githubApiUrl, String credentialsId) {
    def changedFiles = []
    String remoteUrl = getRemoteUrl()
    String currentCommit = getRevisionFromBuild(currentBuild, remoteUrl)
    String lastCommit = getRevisionFromBuild(currentBuild.previousSuccessfulBuild, remoteUrl)

    if (remoteUrl == null || currentCommit == null || lastCommit == null) {
        echo "INFO: Not enough information to determine any file changes."
        return [changedFiles: changedFiles, foundSuccessfulBuild: false]
    }

    Map repoDetails = getOrganizationRepoFromRemote(remoteUrl)

    if (!repoDetails.organization || !repoDetails.repository) {
        echo "ERROR: Could not find organization or repository from ${remoteUrl}"
        return [changedFiles: changedFiles, foundSuccessfulBuild: false]
    }

    String org = repoDetails.organization

    String apiUrl = "${githubApiUrl}/repos/${repoDetails.organization}/${repoDetails.repository}/compare/${lastCommit}...${currentCommit}"

    Map response
    if (credentialsId == "") {
        echo "INFO: Getting file changes via anonymous access"
        def resp = httpRequest(
            url: apiUrl.toString(),
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            httpMode: 'GET',
            validResponseCodes: '100:399',
        )
        response = readJSON(text: resp.getContent())
    } else {
        withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
            def resp = httpRequest(
                url: apiUrl.toString(),
                acceptType: 'APPLICATION_JSON',
                contentType: 'APPLICATION_JSON',
                httpMode: 'GET',
                validResponseCodes: '100:399',
                customHeaders = [
                    [name: 'Authorization', value: "token ${GITHUB_TOKEN}", maskValue: true]
                ]
            )
        }
        response = readJSON(text: resp.getContent())
    }

    changedFiles = response.files ?: []

    return [changedFiles: changedFiles, foundSuccessfulBuild: true]
}

String getRemoteUrl() {
    List<RemoteConfig> repositories = scm.getRepositories()
    if (repositories.size() < 1) {
        echo "ERROR: No remote repository detected. Is the Jenkinsfile fetched from source?"
        return null
    }

    ArrayList<String> urls = []
    repositories.each { repo ->
        repo.getURIs().each { uriish ->
            urls.add(uriish.toString())
        }
    }

    if (urls.size() < 1) {
        echo "ERROR: No remote repository URLs found. This should not happen."
        return null
    } else if (urls.size() > 1) {
        echo "WARNING: ${urls.size()} remote URLs found. Using ${urls[0]}"
    }

    echo "INFO: Found remote URL ${urls[0]}"
    return urls[0];
}

Map getOrganizationRepoFromRemote(String url) {
    List<String> urlParts = url.replaceAll("\\.git\$", "").split("/")
    return [organization: urlParts[urlParts.size() - 2], repository: urlParts[urlParts.size() - 1]]
}

String getRevisionFromBuild(RunWrapper build, String remoteUrl) {
    if (build == null) {
        echo "INFO: No build given, so unable to find revision id"
        return null
    }

    String gitHash = null

    build.rawBuild.getActions().each { action ->
        if (action instanceof BuildData) {
            if (action.getRemoteUrls().contains(remoteUrl)) {
                echo "INFO: Matched SCM URL for build action"
                gitHash = action.lastBuiltRevision.getSha1String()
                return gitHash
           }
        }
    }

    if (gitHash == null) {
        echo "WARNING: No matching git hash found"
    }

    return gitHash
}

Map interrogateBuild(Map params = [:]) {
    Map results = filesChangedSinceLastSuccessfulBuild("https://api.github.com", params.credentialsId)
    echo "filesChangedSinceLastSuccessfulBuild: ${results}"
    return results


    String url = getRemoteUrl()
    String currentCommit = getRevisionFromBuild(currentBuild, url)
    String lastCommit = getRevisionFromBuild(currentBuild.previousSuccessfulBuild, url)
    if (url == null || currentCommit == null || lastCommit == null) {
        echo "WARNING: Could not find all the information needed to identify changes."
    } else {
        echo "INFO: Should look for ${url}/${lastCommit}...${currentCommit}"
        String noGit = url.replaceAll("\\.git\$", "")
        echo "Without .git: ${noGit}"
        List<String> urlParts = noGit.split("/")
        echo "Organization = ${urlParts[urlParts.size()-2]} repo = ${urlParts[urlParts.size()-1]}"
    }
    /*
    int i = 0
    echo "scm = ${scm}"
    echo "repositories = ${scm.getRepositories()}"
    echo "URIs = ${scm.getRepositories()[0].getURIs()}"
    String url = ${scm.getRepositories()[0].getURIs()[0].toString()}
    echo "First URL = ${url}"
    while (i < 2) {
        echo "Build ${build.id} had result ${build.result}"
        echo "Build variables = ${build.absoluteUrl}"
        echo "Build actions = ${build.rawBuild.getActions()}"
        echo "Interrogating build actions:"
        build.rawBuild.getActions().each {
            echo "BuildAction class name is ${it.class.name}"
            if (it instanceof hudson.plugins.git.util.BuildData) {
                echo "Last built revision = ${it.lastBuiltRevision} [${it.lastBuiltRevision.class.name}]"
                echo "Last built revision sha1 = ${it.lastBuiltRevision.getSha1String()}"
                echo "Remote URLs: ${it.getRemoteUrls()}"
                echo "SCM Name: ${it.getScmName()}"
            }
        }
        build = build.previousSuccessfulBuild
        i++
    }
    */
}
