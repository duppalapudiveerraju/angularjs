import groovy.json.JsonOutput
pipeline {
   agent {
    label 'master'
      }
     parameters {
         string(name: 'Service_Name', defaultValue: 'Provide servicename', description: 'Which service needs to build,test&deploy')
        choice(choices: 'master\nfrontend\nazure\nsharearideazure\nazurepreprod', description: 'Select the environment', name: 'Environment')
         choice(choices: 'master\ntest\nsit\ndevelop\nmsf-api', description: 'Select the branch name', name: 'Branch')
             }
         
 environment {
     DOCKER_HOST = 'tcp://127.0.0.1:4243'
      scannerHome = tool 'sonar1'
      // https_proxy = 'https://10.144.106.132:8678'
      // http_proxy = 'http://10.144.106.132:8678'
      // no_proxy = '10.157.242.21'
      // MAVEN_HOME = '/usr/ubuntu/maven'
      // PATH = '$PATH:$MAVEN_HOME/bin'
     // BRANCH_NAME = 'test'
      }
stages {
      stage ('checkout') {
        steps {
          node ('jenkins-slave01') {
            checkout scm
             }
         }
       }
      stage ('package') {
        steps {
          node ('master') {
              sh 'java -version'
              sh 'mvn --version'
              sh 'cd hrss-util; mvn clean install'
              sh "cd ${params.Service_Name}; mvn clean install"
              // stash includes: "/var/lib/jenkins/workspace/kishore@2/gateway-sevice/*", name: "first_stash"
              }
              }
            }
  stage ('junit test') {
         steps {
           node ('master') {
             // dir ('first_stash') {
             // unstash "first_stash"
              sh "cd ${params.Service_Name}; mvn test"
          // }
           }
     }
     }
  stage('sonar') {
          steps {
          node ('master') {
             withSonarQubeEnv('sonar1') {
               sh "cd ${params.Service_Name}; ${scannerHome}/bin/sonar-scanner -e -Dsonar.projectName=${params.Service_Name} -Dsonar.projectKey=${params.Service_Name} "
                  }
               }
            }
            }
         stage("wait_prior_starting_smoke_testing") {
         when {
               expression {
                  return "${params.Branch}" != 'develop';
                  }
                  }
             steps {
             node ('master') {
                echo 'Waiting for 10 minutes for deployment to complete prior starting smoke testing'
                  sleep 100 // seconds
                  }
                }
                }
          stage('sonarstatus') {
          when {
               expression {
                  return "${params.Branch}" != 'develop';
                  }
                  }
             options { timeout(time: 1, unit: 'HOURS') }
               steps {
                  echo 'Checking quality gate...'
              script {
                    def qualitygate = waitForQualityGate()
                       if (qualitygate.status != "OK") {
                            echo "Pipeline aborted due to quality gate coverage failure: ${qualitygate.status}"


                            def payload = JsonOutput.toJson([ "fields" : [ "project": [ "key": "HRSSTES" ],

                                               "summary": "jobsuccess.",

                                              "description": "Creating of an issue using project keys and issue type names using the REST API",

                                               "issuetype": [ "name": "Bug"],

                                                "assignee": [ "name": "jiocloud.adm" ] ] ])
                            sh "curl -D- -u 'jiocloud.adm:jio@1234' -X POST --data \'${payload}\'  -H 'Content-Type: application/json' 'http://10.157.242.35:8080/rest/api/2/issue/' --insecure"
                     } else {
                        echo "Quality Gate has passed"
                        }
                     }
                   }
                 }
      stage ('fortifyscan') {
       steps {
         node ('windows') {
           checkout scm
             }
         node ('windows') {
         bat 'sourceanalyzer.exe -b test -clean'
         bat 'sourceanalyzer.exe -b test "%WORKSPACE%"'
         bat 'sourceanalyzer.exe -b test -export-build-session file.mbs'
        // bat 'cloudscan -sscurl http://10.157.255.239:8080/ssc/ -ssctoken 4a6e49e8-58b5-49ae-9f53-c2a5e8d13550 start -upload -versionid 10034 -uptoken 9d07cac1-f43a-4cc9-8e68-f388fec8c60d -b test -scan -autoheap -build-label Xmx2G                      -build-project Share A Ride-build-version 1.0'
             }
           }
        }
     stage ('image push') {
     steps {
              node ('master') {
                sh 'docker login 10.157.242.60:8082 -u jiocloud.adm -p Reliance@231'
                sh "docker tag ${params.Service_Name} 10.157.242.60:8082/${params.Service_Name}"
                sh "docker rmi ${params.Service_Name}"
                sh "docker push 10.157.242.60:8082/${params.Service_Name}"
                sh 'docker images -qf dangling=true | xargs docker rmi'
                sh "cd ${params.Service_Name}/target; curl -v -u admin:admin123 --upload-file ${params.Service_Name}-1.0-SNAPSHOT.jar http://10.157.242.60:8081/repository/develop"
                }
              }
              }
    stage ('deploy') {
          steps {
             node ('ansible-slave') {
                   sh "cd /opt/;ansible-playbook ansible.yml --extra-vars variable_host=${params.Environment} --extra-vars Service_Name=${params.Service_Name}"
                 }
             }
         }
         }
         post {
           always{
             cleanWs()
             }
            } 
         }
