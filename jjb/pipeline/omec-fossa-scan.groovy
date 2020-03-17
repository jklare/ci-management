// Copyright 2020-present Open Networking Foundation
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

// omec-fossa-scan.groovy
// checks an omec-project repo against fossa in a docker container

pipeline {

    agent {
        docker {
            image "fossa-verify:latest"
            label "${params.buildNode}"
        }
    }

    options {
        timeout(15)
    }

    stages {

        stage ("Clean Workspace") {
            steps {
                sh 'rm -rf *'
            }
        }

        stage ("Checkout Pull Request") {
            steps {
                checkout([
                    $class: 'GitSCM',
                    userRemoteConfigs: [[ url: "https://github.com/${params.ghprbGhRepository}", refspec: "pull/${params.ghprbPullId}/head" ]],
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${params.project}"]],
                    ],
                )
            }
        }

        stage ("Perform License Scan") {
            steps {
                withCredentials([string(credentialsId: 'fossa-api-key', variable: 'FOSSA_API_KEY')]) {
                    sh  """
                        #!/usr/bin/env bash

                        cd ${params.project}
                        git checkout FETCH_HEAD
                        git show

                        echo "Testing project: ${params.project}"

                        echo "Run 'fossa init'"
                        fossa init --no-ansi --verbose

                        echo "Contents of .fossa.yml generated by 'fossa init':"
                        cat .fossa.yml

                        echo "Run 'fossa analyze'"
                        fossa analyze --no-ansi --verbose

                        echo "Get FOSSA test results with 'fossa test'"
                        fossa test --no-ansi --verbose
                        """

                }
            }
        }
    }
}
