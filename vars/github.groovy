/**
 * Returns true if the path(s) have been changed and false otherwise
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

void interrogateBuild() {
    def build = currentBuild
    int i = 0
    while (i < 2) {
        echo "Build ${build.id} had result ${build.result}"
        echo "Build variables = ${build.absoluteUrl}"
        echo "Build actions = ${build.rawBuild.getActions()}"
        echo "Interrogating build actions:"
        build.rawBuild.getActions().each {
            echo "BuildAction class name is ${it.class.name}"
            if (it.hasProperty("lastBuiltRevision")) {
                echo "Last built revision = ${it.lastBuiltRevision} [${it.lastBuiltRevision.class.name}]"
                echo "Last built revision sha1 = ${it.lastBuiltRevision.getSha1()}"
            }
        }
        build = build.previousBuild
        i++
    }
}
