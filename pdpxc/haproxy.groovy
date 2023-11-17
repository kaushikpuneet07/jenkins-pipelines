library changelog: false, identifier: "lib@master", retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
])


pipeline {
  agent {
    label 'min-centos-7-x64'
  }
  environment {
    PATH = '/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/home/ec2-user/.local/bin';
    MOLECULE_DIR = "molecule/pdmysql/haproxy";
  }
  parameters {
    choice(
        name: 'REPO',
        description: 'Repo for testing',
        choices: [
            'testing',
            'experimental',
            'release'
        ]
    )
    string(
        defaultValue: '8.0.34',
        description: 'PXC version for test',
        name: 'VERSION'
    )
    string(
      name: 'HAPROXY_VERSION',
      defaultValue: '2.8.1',
      description: 'Full haproxy version. Used as version and docker tag'
    )
    choice(
      name: 'DOCKER_ACC',
      description: 'Docker repo to use: percona or perconalab',
      choices: [
        'perconalab',
        'percona'
      ]
    )
    string(
    name: 'TESTING_REPO',
    defaultValue: 'https://github.com/Percona-QA/package-testing.git',
    description: 'Repo for package-testing repository'
    )
    string(
      name: 'TESTING_BRANCH',
      defaultValue: 'master',
      description: 'Branch for package-testing repository'
    )
  }

  options {
    withCredentials(moleculePdpxcJenkinsCreds())
    disableConcurrentBuilds()
  }

  stages {
    stage('Checkout') {
      steps {
        deleteDir()
        git poll: false, branch: TESTING_BRANCH, url: TESTING_REPO
      }
    }

    stage("Run parallel") {
      parallel {
        stage ('Molecule') {
          stages {
            stage ('Molecule: Prepare') {
              steps {
                script {
                  installMolecule()
                }
              }
            }
            stage ('Molecule: Create virtual machines') {
              steps {
                script{
                  moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "create", "ubuntu-bionic")
                }
              }
            }
            stage ('Molecule: Run playbook for test') {
              steps {
                script{
                  moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "converge", "ubuntu-bionic")
                }
              }
            }
            stage ('Molecule: Start testinfra tests') {
              steps {
                script{
                  moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "verify", "ubuntu-bionic")
                }
              }
            }
            stage ('Molecule: Start Cleanup ') {
              steps {
                script {
                  moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "cleanup", "ubuntu-bionic")
                }
              }
            }
          }
          post {
            always {
              script {
                moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "destroy", "ubuntu-bionic")
              }
            }
          }
        }
        stage ('Docker') {
          agent {
            label 'docker'
          }
          stages {
            stage ('Docker: Run trivy analyzer') {
              steps {
                catchError {
                  sh """
                      TRIVY_VERSION=\$(curl --silent 'https://api.github.com/repos/aquasecurity/trivy/releases/latest' | grep '"tag_name":' | tr -d '"' | sed -E 's/.*v(.+),.*/\\1/')
                      wget https://github.com/aquasecurity/trivy/releases/download/v\${TRIVY_VERSION}/trivy_\${TRIVY_VERSION}_Linux-64bit.tar.gz
                      sudo tar zxvf trivy_\${TRIVY_VERSION}_Linux-64bit.tar.gz -C /usr/local/bin/
                      wget https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/junit.tpl
                      /usr/local/bin/trivy -q image --format template --template @junit.tpl  -o trivy-hight-junit.xml \
                                          --timeout 10m0s --ignore-unfixed --exit-code 1 --severity HIGH,CRITICAL ${DOCKER_ACC}/haproxy:${HAPROXY_VERSION}
                  """
                }
              }
              post {
                always {
                  junit testResults: "*-junit.xml", keepLongStdio: true, allowEmptyResults: true, skipPublishingChecks: true
                }
              }
            }
            stage('Docker: Run tests') {
              steps {
                sh '''
                  # run test
                  export PATH=${PATH}:~/.local/bin
                  sudo yum install -y python3 python3-pip
                  rm -rf package-testing
                  git clone ${TESTING_REPO} -b ${TESTING_BRANCH} --depth 1
                  cd package-testing/docker-image-tests/haproxy
                  pip3 install --user -r requirements.txt
                  ./run.sh
                '''
              }
              post {
                always {
                  junit 'package-testing/docker-image-tests/haproxy/report.xml'
                }
              }
            }
          }
        }
      }
    }
  }
}
