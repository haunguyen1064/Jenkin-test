pipeline {
  agent {
    node {
      label 'STORYBOOK-DEMO' }
  }

  parameters {
		string(defaultValue: 'master', description: 'Choose which Newman GIT Branch to run', name: 'GIT_BRANCH_NAME')
	}

  environment {
    BUILD_RESULTS_EMAIL_LIST = "nlethi@rbbn.com;"
    GIT_NAME = "as-prov-ui"
    APP_NAME = "AS-PROV-UI-DEMO"
    COMMON_LIB_VERSION = '11.0.x'

		PLANO_RIBBON_COMMON__REPOSITORY = "ribbon_common-npm-prod-plano/"
		PLANO_ARTIFACTORY_URL = "http://rcplc7artent.genband.com:8081/artifactory/"
    PLANO_ARTI_LOADBUILD_TOKEN = credentials('f8673777-0c46-46c1-96ff-fcdffe5084bb')
    WGET_OPTS = "--header=Authorization: Bearer $PLANO_ARTI_LOADBUILD_TOKEN"
  }

  options {
    timestamps()
    timeout(time: 4, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr:'20'))
  }

  stages {
    stage('Install Build Dependencies') {
      steps {
        // Setup OS VM
        sh '''
          yum install -y wget firefox ant

          curl --silent --location https://rpm.nodesource.com/setup_10.x | sudo bash -
          sudo yum -y install nodejs
          npm install npm@6.14.11 -g

          npm install -g typescript pm2 protractor tslint
          npm install -g --save-dev @angular/cli@11.1.0

          sudo npm cache clean -f
          #sudo npm install -g n
          #sudo n stable

        '''
      }
    }

    stage('Build') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'f9678791-baea-418f-a23f-a4d6a55487f6', usernameVariable: 'VSEBLD', passwordVariable: 'VSEBLD_PASS')]) {
          sh '''
            rm -rf as-prov-ui
            git clone http://$VSEBLD:$VSEBLD_PASS@bitbucket.genband.com/scm/ng/as-prov-ui.git
            cd as-prov-ui
            git checkout $GIT_BRANCH_NAME
            git pull origin $GIT_BRANCH_NAME
            npm ci

            wget "${WGET_OPTS}" -O ~/.npmrc ${PLANO_ARTIFACTORY_URL}ribbon_common-npm-prod-plano/Dependencies/.npmrc
            npm install "rbn-common-lib@${COMMON_LIB_VERSION}" --registry "${PLANO_ARTIFACTORY_URL}api/npm/${PLANO_RIBBON_COMMON__REPOSITORY}"

            pm2 delete -s ${APP_NAME} || :
            pm2 start npm --name=${APP_NAME} -- run startremote
          '''
        }
      }
    }

  }

  post {
    failure {
      emailext body: "Build URL is: ${BUILD_URL}",
      recipientProviders: [
        [$class: 'DevelopersRecipientProvider']
      ],
      subject: "[Jenkins] CMP UI DEMO $GIT_NAME - Build #${BUILD_NUMBER} - BUILD FAILING # ATTENTION REQUIRED",
      to: "$BUILD_RESULTS_EMAIL_LIST"
    }

    unstable {
      emailext body: "AS PROV UI DEMO build UNSTABLE.\n",
      recipientProviders: [
        [$class: 'DevelopersRecipientProvider']
      ],
      subject: "[Jenkins] CMP UI DEMO $GIT_NAME - Build #${BUILD_NUMBER} - BUILD Unstable # ATTENTION REQUIRED",
      to: "$BUILD_RESULTS_EMAIL_LIST"
    }

    always {
      // Terminate Build VM
      sh "curl -X POST http://vsebld:5f0805a88be7a448a518ae917cb9087f@gbpljnk02.genband.com:8081/computer/${env.NODE_NAME}/scheduleTermination"
    }
  }
}