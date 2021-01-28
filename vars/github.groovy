/**
 * Returns true if the path(s) have been changed and false otherwise. Note that for pull requests,
 * the files changed are available in the build information. The credentials and GitHub API will
 * only be used for branch builds where CHANGE_ID is not set.
 *
 * This check falls back on the git command-line for branch builds, so it requires an allocated
 * executor and must be executed from inside a git repository that has its full history checked
 * out.
 *
 * :param path: Name of the file or path prefix where changes should be looked for
 * :param paths: A list of file or path prefix names where changes should be looked for
 */
boolean fileChangedIn(Map params = [:]) {
    String path = params.path ?: ''
    def paths = params.paths ?: []

    if (paths == [] && path == '') {
        assert path : 'ERROR: Must pass either path or paths'
    } else if (path) {
        paths << path
    }

    def changedFiles = []
    Map result
    if (env.CHANGE_ID) {
        pullRequest.files.each { changedFiles << it.filename }
    } else {
        echo "INFO: Not a pull request; looking for changes since the last successful build"
        result = filesChangedSinceLastSuccessfulBuild()
        if (!result.foundSuccessfulBuild) {
            echo "WARNING: Did not find a prior successful build. Forcibly saying yes"
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
Map filesChangedSinceLastSuccessfulBuild() {
    String remoteUrl = getRemoteUrl()
    String currentCommit = getRevisionFromBuild(currentBuild, remoteUrl)
    String lastCommit = getRevisionFromBuild(currentBuild.previousSuccessfulBuild, remoteUrl)

    if (remoteUrl == null || currentCommit == null || lastCommit == null) {
        echo "INFO: Not enough information to determine any file changes."
        return [changedFiles: [], foundSuccessfulBuild: false]
    }
    List<String> changedFiles = git.diffFiles(targetRef: lastCommit, sourceRef: currentCommit)
    return [changedFiles: changedFiles, foundSuccessfulBuild: true]
}

String getRemoteUrl() {
    List repositories = scm.getRepositories()
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

String getRevisionFromBuild(def build, String remoteUrl) {
    if (build == null) {
        echo "INFO: No build given, so unable to find revision id"
        return null
    }

    String gitHash = null

    build.rawBuild.getActions().each { action ->
        if (action.hasProperty("getRemoteUrls")) {
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
