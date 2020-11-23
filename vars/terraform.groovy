/**
 * This module wraps terraform functionality for terraform applies
 */
import groovy.transform.Field

@Field String terraformVersion = '0.13.5'

/**
 * Executes the given function within a terraform container
 *
 * :param awsCredentials: The AWS Credentials to set up for access to AWS
 */
void withTerraform(Map params, Closure body) {
    String terraformFileName = "terraform_${terraformVersion}_linux_amd64.zip"
    String terraformUrl = "https://releases.hashicorp.com/terraform/${terraformVersion}/${terraformFileName}"
    docker.image('alpine:3.12').inside("-e HOME=\"${env.WORKSPACE}\"") {
        // Set up AWS credentials if provided
        if (params.awsCredentials) {
            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                              credentialsId: 's3-terraform-state-credentials',
                              accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                              secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                writeFile(file: "${env.WORKSPACE}/.aws/credentials",
                          text: "[default]\naws_access_key_id=${AWS_ACCESS_KEY_ID}\naws_secret_access_key=${AWS_SECRET_ACCESS_KEY}\n")
            }
        }

        echo "INFO: Downloading terraform"
        sh(label: "Download terraform", script: """#!/bin/sh -ex
            cd ${env.WORKSPACE}
            if [ -f terraform -a -x terraform ]; then
                echo "INFO: Terraform already present and executable"
            else
                echo "INFO: Fetching and unzipping terraform"
                rm -f terraform
                wget ${terraformUrl}
                unzip ${terraformFileName}
                rm ${terraformFileName}
            fi
        """)

        body()
    }
}

/**
 * Executes a terraform plan and saves the plan to a local file
 *
 * :param fileName: The name of the file to save the terraform plan to. Default is terraform.plan
 * :param args: Extra command-line arguments to pass to `terraform plan`
 * :param errorOnFailure: If true, throw a build error if terraform plan fails. Default true
 *
 * :returns succeeded: Returns true if the plan succeeded and false otherwise
 */
boolean plan(Map params = [:]) {
    String fileName = params.fileName ?: 'terraform.plan'
    String args = params.args ?: ''

    return terraformCommand('plan', "${args} -out ${fileName}", params.getOrDefault('errorOnFailure', true))
}

/**
 * Executes a terraform initialization step
 *
 * :param args: Extra command-line arguments to pass to terraform init
 * :param errorOnFailure: If true, throw a build error if terraform init fails. Default true
 *
 * :return succeeded: If the initialization step succeeded, return True
 */
boolean init(Map params = [:]) {
    return terraformCommand('init', params.args ?: '', params.getOrDefault('errorOnFailure', true))
}

/**
 * Executes a terraform apply with automatic approval
 *
 * :param fileName: The name of the file that contains the output of the apply. Default prints to stdout
 * :param args: Extra arguments to pass to terraform apply
 * :param errorOnFailure: If true, throw a build error if terraform apply fails. Default true
 *
 * :return succeeded: If the terraform apply succeeded, return True. Otherwise false
 */
boolean apply(Map params = [:]) {
    boolean errorOnFailure = params.getOrDefault('errorOnFailure', true)
    String args = params.args ?: ''
    if (params.fileName) {
        return terraformCommand('apply', "${args} -auto-approve | tee ${params.fileName}", errorOnFailure)
    }
    return terraformCommand('apply', "${args} -auto-approve", errorOnFailure)
}

/**
 * Internal function for running terraform command. Backwards compatibility is not guaranteed
 */
boolean terraformCommand(String command, String args, boolean errorOnFailure) {
    boolean succeeded = sh(label: "Terraform ${command}",
                           script: "${env.WORKSPACE}/terraform ${command} ${args}",
                           returnStatus: true) == 0

    if (!succeeded && errorOnFailure) {
        error("Failed terraform ${command} - see the logs for additional details")
    }

    return succeeded
}
