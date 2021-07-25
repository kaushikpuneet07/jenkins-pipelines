library changelog: false, identifier: "lib@orchestrator_tests", retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
])


pipeline {
  agent {
  label 'min-centos-7-x64'
  }
  environment {
      PATH = '/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/home/ec2-user/.local/bin';
      MOLECULE_DIR = "molecule/pdmysql/orchestrator";
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
            defaultValue: '8.0.25',
            description: 'PDMYSQL version for test',
            name: 'VERSION'
         )
        string(
            defaultValue: '2.3.10',
            description: 'HAProxy version for test',
            name: 'HAPROXY_VERSION'
         )
        string(
            defaultValue: '8.0.25',
            description: 'PXB version for test',
            name: 'PXB_VERSION'
         )
        string(
            defaultValue: '3.3.1',
            description: 'Percona toolkit version for test',
            name: 'PT_VERSION'
         )
        string(
            defaultValue: '3.2.5',
            description: 'Percona orchestrator version for test',
            name: 'ORCHESTRATOR_VERSION'
         )
  }
  options {
          withCredentials(moleculeDistributionJenkinsCreds())
          disableConcurrentBuilds()
  }
  stages {
    stage('Checkout') {
      steps {
            deleteDir()
            git poll: false, branch: 'master', url: 'https://github.com/Percona-QA/package-testing.git'
        }
    }
    stage ('Prepare') {
      steps {
          script {
              installMolecule()
            }
        }
    }
    stage ('Create virtual machines') {
      steps {
          script{
              moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "create", "ubuntu-bionic")
            }
        }
    }
    stage ('Run playbook for test') {
      steps {
          script{
              moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "converge", "ubuntu-bionic")
            }
        }
    }
    stage ('Start testinfra tests') {
      steps {
            script{
              moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "verify", "ubuntu-bionic")
            }
        }
    }
    stage ('Start Cleanup ') {
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
