// Copyright 2017-2023 Open Networking Foundation (ONF) and the ONF Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/* docker-publish pipeline */
pipeline {

  /* executor is determined by JJB */
  agent {
    label "${params.buildNode}"
  }
  stages {

    stage('checkout') {
      steps {
        checkout([
          $class: 'GitSCM',
          userRemoteConfigs: [[ url: "${params.gitUrl}", ]],
          branches: [[ name: "${params.gitRef}", ]],
          extensions: [
            [$class: 'WipeWorkspace'],
            [$class: 'RelativeTargetDirectory', relativeTargetDir: "${params.projectName}"],
            [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false],
            [$class: 'SubmoduleOption', recursiveSubmodules: true],
          ],
        ])
        script {
          git_tags = sh(script:"cd $projectName; git tag -l --points-at HEAD", returnStdout: true).trim()
        }
      }
    }

    stage('build'){
      steps {
        sh( script: """
          #!/usr/bin/env bash
          set -eu -o pipefail

          # checked out in a subdir so the log can be in WORKSPACE
          cd "$projectName"

          # set registry/repository variables
          export DOCKER_REGISTRY="$dockerRegistry"
          export DOCKER_REPOSITORY="$dockerRepo/"

          # Build w/branch
          echo "Building image with branch"
          $extraEnvironmentVars DOCKER_TAG="$branchName" make docker-build 2>&1 | tee "$WORKSPACE/docker-build.log"

          # Build w/tags if they exist
          if [ -n "$git_tags" ]
          echo "Tags found in git, building:"
          echo "$git_tags"

          then
            for tag in $git_tags
            do
              # remove leading 'v' on funky golang tags
              clean_tag=\$(echo \$tag | sed 's/^v//g')
              echo "Building image with tag: \$clean_tag (should reuse cached layers)"
              $extraEnvironmentVars DOCKER_TAG="\$clean_tag" make docker-build
            done
          fi
        """)
      }
    }

    stage('push'){
      steps {
        script {
          withDockerRegistry([credentialsId: 'docker-artifact-push-credentials']) {
            sh( script:"""
              #!/usr/bin/env bash
              set -eu -o pipefail

              # checked out in a subdir so the log can be in WORKSPACE
              cd "$projectName"

              # set registry/repository variables
              export DOCKER_REGISTRY="$dockerRegistry"
              export DOCKER_REPOSITORY="$dockerRepo/"

              # Push w/branch
              echo "Pushing image with branch"
              $extraEnvironmentVars DOCKER_TAG="$branchName" make docker-push 2>&1 | tee "$WORKSPACE/docker-push.log"

              # Push w/tags if they exist
              if [ -n "$git_tags" ]
              echo "Tags found in git, pushing:"
              echo "$git_tags"
              then
                for tag in $git_tags
                do
                  # remove leading 'v' on funky golang tags
                  clean_tag=\$(echo \$tag | sed 's/^v//g')
                  echo "Pushing image with tag: \$clean_tag (should reuse cached layers)"
                  $extraEnvironmentVars DOCKER_TAG="\$clean_tag" make docker-push
                done
              fi
            """)
          }
        }
      }
    }
  }

  post {
    always {
      archiveArtifacts artifacts: 'docker-*.log', fingerprint: true
      deleteDir()
    }
    failure {
      step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: "${params.maintainers}", sendToIndividuals: false])
    }
  }
}
