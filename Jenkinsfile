pipeline {
    agent { label "maven" }
    options {
        timestamps()
    }
    stages {
        stage("Git Checkout") {
            steps {
                git branch: 'main', changelog: false, poll: false, url: 'https://github.com/dima2212/spring-petclinic.git'
            }
        }

        stage("Lint") {
            steps {
                container("maven") {
                    sh "mvn validate"
                }
            }
        }

        stage("Get App Version") {
            steps {
                container("maven") {
                    script {
                        def pom_version = sh(
                            script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout",
                            returnStdout: true
                        ).trim()
                        pom_version = pom_version.replace("-SNAPSHOT", "")
                        ARTIFACT_VERSION = "${pom_version}.${BUILD_NUMBER}"
                        currentBuild.displayName = "${ARTIFACT_VERSION}"
                    }
                }
            }
        }

        stage("Build") {
            steps{
                container("maven"){
                    sh "mvn -B clean package -DskipTests"
                }
            }
        }

        stage("Test") {
            steps{
                container("maven"){
                    sh "mvn test"
                }
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage("Sonarqube Scan"){
            steps {
                container("maven") {
                    withSonarQubeEnv("sonarqube") {
                      sh """
                        mvn sonar:sonar \
                            -Dsonar.projectKey=Pet-Clinic \
                            -Dsonar.projectName='Pet Clinic'
                      """
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                container('docker') {
                    withCredentials([usernamePassword(credentialsId: 'dockerhub-token', passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
                        sh 'echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin'
                        sh """
                            docker build -t "${DOCKER_USERNAME}/petclinic:${ARTIFACT_VERSION}" .
                            docker push "${DOCKER_USERNAME}/petclinic:${ARTIFACT_VERSION}"
                        """
                    }
                }
            }
        }

        stage("Dependency Check") {
            steps {
                container("owasp") {
                    sh """
                        dependency-check.sh \
                            --scan ${WORKSPACE} \
                            --format HTML \
                            --format XML \
                            --out ${WORKSPACE}/reports \
                            --project "spring-pet-clinic" \
                            --disableOssIndex \
                            --data /home/depcheck/dependency-check/data
                    """
                    dependencyCheckPublisher pattern: "**/dependency-check-report.xml"
                    archiveArtifacts artifacts: "**/dependency-check-report.html", onlyIfSuccessful: true
                }
            }
        }
    }
}
