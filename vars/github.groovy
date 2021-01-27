/**
 * Returns true if the path(s) have been changed and false otherwise
 *
 * :param path: Name of the file or path prefix where changes should be looked for
 * :param paths: A list of file or path prefix names where changes should be looked for
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
        Map result = filesChangedSinceLastSuccessfulBuild()
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
        if (anyMatch) {
            echo "INFO: Changed file ${changedFile} matches"
            return true
        }
    }
    echo "INFO: No changed file matches any given path"
    return false
}

// Internal method for finding list of files that have changed since the last build
Map filesChangedSinceLastSuccessfulBuild() {
    def changedFiles = []
    def build = currentBuild
    def lastSuccessfulBuild = currentBuild.previousSuccessfulBuild
    if (lastSuccessfulBuild == null) {
        echo "INFO: No previous successful build found"
        return [changedFiles: changedFiles, foundSuccessfulBuild: false]
    }
    echo "INFO: Looking for changes in build ${build}"
    while (build && !buildSucceeded) {
        echo "INFO: Build has ${build.changeSets.size()} change sets"
        build.changeSets.each { changeSet ->
            echo "INFO: change set has ${changeSet.items.size()} items"
            changeSet.items.each { item ->
                echo "INFO: item has ${item.affectedFiles.size()} affected files"
                item.affectedFiles.each {
                    echo "DEBUG: ${it.path} changed"
                    changedFiles << it.path
                }
            }
        }
        build = build.getPreviousBuild()
        buildSucceeded = build != null && build.result == 'SUCCESS'
    }

    return [changedFiles: changedFiles, foundSuccessfulBuild: buildSucceeded]
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

void interrogateBuild() {
    String url = getRemoteUrl()
    String currentCommit = getRevisionFromBuild(currentBuild, url)
    String lastCommit = getRevisionFromBuild(currentBuild.previousSuccessfulBuild, url)
    if (url == null || currentCommit == null || lastCommit == null) {
        echo "WARNING: Could not find all the information needed to identify changes."
    } else {
        echo "INFO: Should look for ${url}/${lastCommit}...${currentCommit}"
        String noGit = url.replaceAll("\\.git\$", "")
        echo "Without .git: ${noGit}"
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
