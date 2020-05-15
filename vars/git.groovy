// Common git functionality

/**
 * Returns the set of files that have changed between the current commit and
 * a target ref (could be a branch, commit hash, tag, or anything else that
 * git recognizes as a ref)
 *
 * Note, this can only be executed from a Unix agent with `git` available
 *
 * :param targetRef: name of the target ref to use. This is required.
 * :param sourceRef: name of the source ref to use. Default is HEAD.
 * :param fetchRemote: name of the remote to fetch. If not set, nothing is fetched.
 * :param gitCredentialsId: the credentials ID to use to fetch from the remote.
 *                          if not set, anonymous fetching will be attempted
 * :param allChanges: if true, fetch all changes between the refs. if false, only
 *                    the changes from the source will be listed. More explicitly,
 *                    allChanges: true corresponds to "git diff targetRef..sourceRef"
 *                    while allChanges: false corresponds to "git diff targetRef...sourceRef".
 *                    Default is false
 * :returns: list of file names that have changed between the two refs
 */
String[] diffFiles(Map params = [:]) {
    assert params.targetRef : 'targetRef parameter is required'
    String sourceRef = params.sourceRef ?: 'HEAD'
    boolean allChanges = params.getOrDefault('allChanges', false)

    if (params.fetchRemote) {
        // Prune in case we re-use git repositories
        Closure fetchCommand = { ->
            sh(label: 'Fetch Remote', script: "git fetch --prune ${params.fetchRemote}")
        }
        if (params.gitCredentialsId) {
            sshagent([params.gitCredentialsId]) {
                fetchCommand()
            }
        } else {
            fetchCommand()
        }
    }

    String diffCommand = "${params.targetRef}...${sourceRef}"
    if (allChanges) {
        diffCommand = "${params.targetRef}..${sourceRef}"
    }

    String[] files = sh(
        label: 'git diff',
        script: """#!/bin/bash -ex
            git diff --name-only ${diffCommand}
        """,
        returnStdout: true
    ).split('\n')
    return files
}

/**
 * Merges the target branch into the current branch. Note, this must be run on a Unix agent
 *
 * :param currentBranch: name of the branch to be currently checked out. Since Jenkins checks out
 *                       in detached head state, this parameter is required
 * :param targetBranch: name of the branch to merge into currentBranch *from the remote*. Required.
 * :param gitCredentialsId: name of the credentials ID to use to push changes back to git
 * :param push: true if the merged changes should be pushed (default), false otherwise
 * :param remote: name of the remote to push to. Default is origin
 * :returns: Map with boolean elements changesMade, succeeded, and errors
 */
Map merge(Map params = [:]) {
    assert params.currentBranch : 'currentBranch is required'
    assert params.targetBranch : 'targetBranch is required'
    assert params.gitCredentialsId : 'gitCredentialsId is required'

    String remote = params.remote ?: 'origin'
    boolean doPush = params.getOrDefault('push', true)

    boolean hasFailed = false
    String errorMessage
    String mergeContents

    sshagent([params.gitCredentialsId]) {
        hasFailed = sh(label: 'fetch and merge', script: """#!/bin/bash
            set -ex
            echo "NO MERGE" > merge_output.txt

            echo "INFO: fetching from ${remote}"
            git fetch ${remote}

            echo "INFO: checking out ${params.currentBranch}"
            git branch ${params.currentBranch} || echo "Branch already exists!"
            git checkout ${params.currentBranch}
            git fetch ${remote}
            git merge ${remote}/${params.currentBranch}

            echo "INFO: merging target branch"
            git config user.name "Jenkins Automation"
            git config user.email "no-reply@ambermd.org"
            git merge --no-edit ${remote}/${params.targetBranch} > merge_output.txt
        """, returnStatus: true) != 0
    }
    String mergeText = readFile('merge_output.txt').trim()

    if (mergeText == 'NO MERGE') {
        assert hasFailed : 'NO MERGE could not have happened if the script passed'
        String msg = "Failed before merge was attempted. See job output for details"
        return ['changesMade': false, 'succeeded': false, 'errors': msg]
    } else if (hasFailed) {
        errorMessage = "Merge failed: ${mergeText}"
        int status = sh(label: 'abort merge', script: 'git merge --abort', returnStatus: true)
        if (status != 0) {
            echo "ERROR: git merge --abort failed. Ignoring..."
        }
        return ['changesMade': false, 'succeeded': false, 'errors': errorMessage]
    } else if (mergeText == "Already up to date.") {
        echo "INFO: ${params.currentBranch} was already up-to-date with ${params.targetBranch}"
        return ['changesMade': false, 'succeeded': true, 'errors': '']
    } else {
        String msg = ''
        if (doPush) {
            sshagent([params.gitCredentialsId]) {
                hasFailed = sh(label: 'push changes', returnStatus: true,
                               script: "git push ${remote} ${params.currentBranch}") != 0
                msg = hasFailed ? "Failed to push changes to ${remote}" : ''
            }
        } else {
            echo "INFO: push set to false; not pushing"
        }
        return ['changesMade': true, 'succeeded': !hasFailed, 'errors': msg]
    }

    return ['changesMade': false, 'succeeded': true, 'errors': '']
}
