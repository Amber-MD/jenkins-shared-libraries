/**
 * Returns true if the path(s) have been changed and false otherwise
 *
 * :param path: Name of the file or path prefix where changes should be looked for
 * :param paths: A list of file or path prefix names where changes should be looked for
 * :param targetRef: name of the target ref to use. Default is "origin/main"
 * :param sourceRef: name of the source ref to use. Default is HEAD.
 */
boolean github.fileChangedIn(Map params = [:]) {
    String path = params.path ?: ''
    def paths = params.paths ?: []
    if (paths == [] && path == '') {
        assert path : 'ERROR: Must pass either path or paths'
    } else if (path) {
        paths << path
    }

    String targetRef = params.targetRef ?: 'origin/main'
    String sourceRef = params.sourceRef ?: 'HEAD'

    def changedFiles = []
    if (env.CHANGE_ID) {
        echo "INFO: Pull request found - using ${targetRef} as the target reference"
        changedFiles = git.diffFiles(
            targetRef: targetRef,
            sourceRef: sourceRef,
            allChanges: false,
        )
    } else {
        echo "INFO: Not a pull request; looking for changes since the last successful build"
        changedFiles = filesChangedSinceLastSuccessfulBuild()
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
String[] filesChangedSinceLastSuccessfulBuild() {
    def changedFiles = []
    boolean buildSucceeded = false
    def build = currentBuild
    echo "INFO: Looking for changes in build ${build}"
    while (build && !buildSucceeded) {
        buildSucceeded = build.result && build.result == 'SUCCESS'
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
    }

    return changedFiles
}
