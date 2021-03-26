// Copyright 2017-present Open Networking Foundation
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

node {
  // Need this so that deployment_config has global scope when it's read later
  deployment_config = null
}

pipeline {
  /* no label, executor is determined by JJB */
  agent {
    label "${params.buildNode}"
  }
  options {
    timeout(time: 180, unit: 'MINUTES')
  }

  environment {
    KUBECONFIG="$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf"
    VOLTCONFIG="$HOME/.volt/config-minimal"
    PATH="$WORKSPACE/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
  }

  stages {
    stage('Clone kind-voltha') {
      steps {
        step([$class: 'WsCleanup'])
        checkout([
          $class: 'GitSCM',
          userRemoteConfigs: [[
            url: "https://gerrit.opencord.org/kind-voltha",
            refspec: "${kindVolthaChange}"
          ]],
          branches: [[ name: "master", ]],
          extensions: [
            [$class: 'WipeWorkspace'],
            [$class: 'RelativeTargetDirectory', relativeTargetDir: "kind-voltha"],
            [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false],
          ],
        ])
      }
    }
    stage('Clone voltha-system-tests') {
      steps {
        checkout([
          $class: 'GitSCM',
          userRemoteConfigs: [[
            url: "https://gerrit.opencord.org/voltha-system-tests",
            refspec: "${volthaSystemTestsChange}"
          ]],
          branches: [[ name: "${branch}", ]],
          extensions: [
            [$class: 'WipeWorkspace'],
            [$class: 'RelativeTargetDirectory', relativeTargetDir: "voltha-system-tests"],
            [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false],
          ],
        ])
        script {
          sh(script:"""
            if [ '${volthaSystemTestsChange}' != '' ] ; then
              cd $WORKSPACE/voltha-system-tests;
              git fetch https://gerrit.opencord.org/voltha-system-tests ${volthaSystemTestsChange} && git checkout FETCH_HEAD
            fi
            """)
        }
      }
    }
   stage('Clone cord-tester') {
      steps {
        checkout([
          $class: 'GitSCM',
          userRemoteConfigs: [[
            url: "https://gerrit.opencord.org/cord-tester",
            refspec: "${cordTesterChange}"
          ]],
          branches: [[ name: "master", ]],
          extensions: [
            [$class: 'WipeWorkspace'],
            [$class: 'RelativeTargetDirectory', relativeTargetDir: "cord-tester"],
            [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false],
          ],
        ])
      }
    }
    // This checkout allows us to show changes in Jenkins
    // we only do this on master as we don't branch all the repos for all the releases
    // (we should compute the difference by tracking the container version, not the code)
    stage('Download All the VOLTHA repos') {
      when {
        expression {
          return "${branch}" == 'master';
        }
      }
      steps {
       checkout(changelog: true,
         poll: false,
         scm: [$class: 'RepoScm',
           manifestRepositoryUrl: "${params.manifestUrl}",
           manifestBranch: "${params.branch}",
           currentBranch: true,
           destinationDir: 'voltha',
           forceSync: true,
           resetFirst: true,
           quiet: true,
           jobs: 4,
           showAllChanges: true]
         )
      }
    }
    stage ('Initialize') {
      steps {
        sh returnStdout: false, script: "git clone -b master ${cordRepoUrl}/${configBaseDir}"
        script {
           deployment_config = readYaml file: "${configBaseDir}/${configDeploymentDir}/${configFileName}-TT.yaml"
        }
        sh returnStdout: false, script: """
        mkdir -p $WORKSPACE/bin
        bash <( curl -sfL https://raw.githubusercontent.com/boz/kail/master/godownloader.sh) -b "$WORKSPACE/bin"
        cd $WORKSPACE
        if [ "${params.branch}" != "master" ]; then
           cd $WORKSPACE/kind-voltha
           source releases/${params.branch}
        else
           VOLTCTL_VERSION=\$(curl -sSL https://api.github.com/repos/opencord/voltctl/releases/latest | jq -r .tag_name | sed -e 's/^v//g')
        fi

        HOSTOS=\$(uname -s | tr "[:upper:]" "[:lower:"])
        HOSTARCH=\$(uname -m | tr "[:upper:]" "[:lower:"])
        if [ \$HOSTARCH == "x86_64" ]; then
            HOSTARCH="amd64"
        fi
        curl -o $WORKSPACE/bin/voltctl -sSL https://github.com/opencord/voltctl/releases/download/v\${VOLTCTL_VERSION}/voltctl-\${VOLTCTL_VERSION}-\${HOSTOS}-\${HOSTARCH}
        chmod 755 $WORKSPACE/bin/voltctl
        voltctl version --clientonly


        # Default kind-voltha config doesn't work on ONF demo pod for accessing kvstore.
        # The issue is that the mgmt node is also one of the k8s nodes and so port forwarding doesn't work.
        # We should change this. In the meantime here is a workaround.
        if [ "${params.branch}" == "master" ]; then
           set +e


        # Remove noise from voltha-core logs
           voltctl log level set WARN read-write-core#github.com/opencord/voltha-go/db/model
           voltctl log level set WARN read-write-core#github.com/opencord/voltha-lib-go/v3/pkg/kafka
        # Remove noise from openolt logs
           voltctl log level set WARN adapter-open-olt#github.com/opencord/voltha-lib-go/v3/pkg/db
           voltctl log level set WARN adapter-open-olt#github.com/opencord/voltha-lib-go/v3/pkg/probe
           voltctl log level set WARN adapter-open-olt#github.com/opencord/voltha-lib-go/v3/pkg/kafka
        fi
        """
      }
    }

    stage('Functional Tests') {
      environment {
        ROBOT_CONFIG_FILE="$WORKSPACE/${configBaseDir}/${configDeploymentDir}/${configFileName}-TT.yaml"
        ROBOT_FILE="Voltha_TT_PODTests.robot"
        ROBOT_LOGS_DIR="$WORKSPACE/RobotLogs/tt-workflow/FunctionalTests"
      }
      steps {
        sh """
        cd $WORKSPACE/kind-voltha/scripts
        ./log-collector.sh > /dev/null &
        ./log-combine.sh > /dev/null &

        mkdir -p $ROBOT_LOGS_DIR
        if ( ${powerSwitch} ); then
             export ROBOT_MISC_ARGS="--removekeywords wuks -i PowerSwitch -i sanityTT -i sanityTT-MCAST -e bbsim -e notready -d $ROBOT_LOGS_DIR -v POD_NAME:${configFileName} -v KUBERNETES_CONFIGS_DIR:$WORKSPACE/${configBaseDir}/${configKubernetesDir} -v container_log_dir:$WORKSPACE -v OLT_ADAPTER_APP_LABEL:${oltAdapterAppLabel}"
        else
             export ROBOT_MISC_ARGS="--removekeywords wuks -e PowerSwitch -i sanityTT -e bbsim -e notready -d $ROBOT_LOGS_DIR -v POD_NAME:${configFileName} -v KUBERNETES_CONFIGS_DIR:$WORKSPACE/${configBaseDir}/${configKubernetesDir} -v container_log_dir:$WORKSPACE -v OLT_ADAPTER_APP_LABEL:${oltAdapterAppLabel}"
        fi
        make -C $WORKSPACE/voltha-system-tests voltha-tt-test || true
        """
      }
    }

    stage('Failure/Recovery Tests') {
      environment {
        ROBOT_CONFIG_FILE="$WORKSPACE/${configBaseDir}/${configDeploymentDir}/${configFileName}-TT.yaml"
        ROBOT_FILE="Voltha_TT_FailureScenarios.robot"
        ROBOT_LOGS_DIR="$WORKSPACE/RobotLogs/tt-workflow/FailureScenarios"
      }
      steps {
        sh """
        mkdir -p $ROBOT_LOGS_DIR
        if ( ${powerSwitch} ); then
             export ROBOT_MISC_ARGS="--removekeywords wuks -L TRACE -i functionalTT -i PowerSwitch -e bbsim -e notready -d $ROBOT_LOGS_DIR -v POD_NAME:${configFileName} -v KUBERNETES_CONFIGS_DIR:$WORKSPACE/${configBaseDir}/${configKubernetesDir} -v container_log_dir:$WORKSPACE -v OLT_ADAPTER_APP_LABEL:${oltAdapterAppLabel}"
        else
             export ROBOT_MISC_ARGS="--removekeywords wuks -L TRACE -i functionalTT -e PowerSwitch -e bbsim -e notready -d $ROBOT_LOGS_DIR -v POD_NAME:${configFileName} -v KUBERNETES_CONFIGS_DIR:$WORKSPACE/${configBaseDir}/${configKubernetesDir} -v container_log_dir:$WORKSPACE -v OLT_ADAPTER_APP_LABEL:${oltAdapterAppLabel}"
        fi
        make -C $WORKSPACE/voltha-system-tests voltha-tt-test || true
        """
      }
    }

  }
  post {
    always {
      sh returnStdout: false, script: '''
      set +e
      kubectl get pods --all-namespaces -o jsonpath="{range .items[*].status.containerStatuses[*]}{.image}{'\\n'}" | sort | uniq
      kubectl get pods --all-namespaces -o jsonpath="{range .items[*].status.containerStatuses[*]}{.imageID}{'\\n'}" | sort | uniq
      kubectl get nodes -o wide
      kubectl get pods -n voltha -o wide
      kubectl get pods -o wide

      sleep 60 # Wait for log-collector and log-combine to complete

      # Clean up "announcer" pod used by the tests if present
      kubectl delete pod announcer || true

      ## Pull out errors from log files
      extract_errors_go() {
        echo
        echo "Error summary for $1:"
        grep '"level":"error"' $WORKSPACE/kind-voltha/scripts/logger/combined/$1*
        echo
      }

      extract_errors_python() {
        echo
        echo "Error summary for $1:"
        grep 'ERROR' $WORKSPACE/kind-voltha/scripts/logger/combined/$1*
        echo
      }

      extract_errors_go voltha-rw-core > $WORKSPACE/error-report.log
      extract_errors_go adapter-open-olt >> $WORKSPACE/error-report.log
      extract_errors_python adapter-open-onu >> $WORKSPACE/error-report.log
      extract_errors_python voltha-ofagent >> $WORKSPACE/error-report.log
      extract_errors_python onos >> $WORKSPACE/error-report.log

      gzip error-report.log || true
      rm error-report.log || true

      cd $WORKSPACE/kind-voltha/scripts/logger/combined/
      tar czf $WORKSPACE/container-logs.tgz *
      rm * || true

      cd $WORKSPACE
      gzip *-combined.log || true
      rm *-combined.log || true

      # store information on running charts
      helm ls > $WORKSPACE/helm-list.txt || true

      # store information on the running pods
      kubectl get pods --all-namespaces -o wide > $WORKSPACE/pods.txt || true
      kubectl get pods --all-namespaces -o jsonpath="{range .items[*].status.containerStatuses[*]}{.image}{'\\n'}" | sort | uniq | tee $WORKSPACE/pod-images.txt || true
      kubectl get pods --all-namespaces -o jsonpath="{range .items[*].status.containerStatuses[*]}{.imageID}{'\\n'}" | sort | uniq | tee $WORKSPACE/pod-imagesId.txt || true

      '''
      script {
        deployment_config.olts.each { olt ->
          if (olt.type == null || olt.type == "" || olt.type == "openolt") {
             sh returnStdout: false, script: """
             sshpass -p ${olt.pass} scp ${olt.user}@${olt.sship}:/var/log/openolt.log $WORKSPACE/openolt-${olt.sship}.log || true
             sed -i 's/\\x1b\\[[0-9;]*[a-zA-Z]//g' $WORKSPACE/openolt-${olt.sship}.log  # Remove escape sequences
             """
          }
        }
      }
      step([$class: 'RobotPublisher',
        disableArchiveOutput: false,
        logFileName: '**/log*.html',
        otherFiles: '',
        outputFileName: '**/output*.xml',
        outputPath: 'RobotLogs',
        passThreshold: 100,
        reportFileName: '**/report*.html',
        unstableThreshold: 0
        ]);
      archiveArtifacts artifacts: '*.log,*.gz,*.tgz'
    }
    unstable {
      step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: "${notificationEmail}", sendToIndividuals: false])
    }
  }
}
