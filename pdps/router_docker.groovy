library changelog: false, identifier: "lib@master", retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
])


pipeline {
  agent {
      label "docker-32gb"
  }

  parameters {
    choice(
      name: 'DOCKER_ACC',
      description: 'Docker repo to use: percona or perconalab',
      choices: [
        'perconalab',
        'percona'
      ]
    )
    string(
      defaultValue: '8.0.32',
      description: 'Full percona-mysql-router version. Used as version and docker tag',
      name: 'ROUTER_VERSION'
    )
    string(
      defaultValue: '8.0.32-24',
      description: 'Full PS version to test with router',
      name: 'PS_VERSION'
    )
    string(
    defaultValue: 'https://github.com/Percona-QA/package-testing.git',
    description: 'Repo for package-testing repository',
    name: 'TESTING_REPO'
   )
    string(
      defaultValue: 'master',
      description: 'Branch for package-testing repository',
      name: 'TESTING_BRANCH'
    )
  }

  stages {
    stage('Run test') {
      steps {
          script {
            currentBuild.displayName = "#${BUILD_NUMBER}-${DOCKER_ACC}-${ROUTER_VERSION}"
            currentBuild.description = "${PS_VERSION}"
          }
          sh '''
            # run test
            export PATH=${PATH}:~/.local/bin
            sudo yum install -y python3 python3-pip
            rm -rf package-testing
            git clone ${TESTING_REPO} -b ${TESTING_BRANCH} --depth 1
            cd package-testing/docker-image-tests/percona-mysql-router
            pip3 install --user -r requirements.txt
            ./run.sh
          '''
      } //end steps
    } //end Run test stage
  } //end stages
  post {
    always {
      junit 'package-testing/docker-image-tests/percona-mysql-router/report.xml'
    }
  }
}
