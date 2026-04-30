// ⚠️ IMPORTANT: This pipeline is designed to run on Jenkins agents provisioned by the Kubernetes plugin.
//
// - The `container('maven')`, `container('docker')`, and `container('owasp')` steps
//   REQUIRE a Kubernetes Pod Template with containers named exactly:
//     - maven
//     - docker
//     - owasp
//
// - The agent must be configured with a pod template that includes these containers,
//   otherwise the pipeline will fail with "container not found" errors.
//
// - This will NOT work on a standard Jenkins agent (e.g., static VM) without modification.
//   If running outside Kubernetes, replace `container(...)` blocks with direct `sh` steps
//   and ensure required tools are installed on the agent.
//
// - Docker builds assume the container has access to a Docker daemon (e.g., docker-in-docker
//   or mounted /var/run/docker.sock). Misconfiguration will cause build/push failures.
//
// Make sure your Jenkins Kubernetes plugin and pod templates are correctly configured before running.
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
                        def version = sh(
                            script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout",
                            returnStdout: true
                        ).trim().replace('-SNAPSHOT', '')

                        ARTIFACT_VERSION = "${version}.${BUILD_NUMBER}"
                        currentBuild.displayName = ARTIFACT_VERSION
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
                    sh "mvn verify"
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
                    sh """
                        set -euo pipefail

                        echo "\$DOCKER_PASSWORD" | docker login -u "\$DOCKER_USERNAME" --password-stdin

                        IMAGE="\$DOCKER_USERNAME/petclinic"

                        docker build \
                            -t "\$IMAGE:${ARTIFACT_VERSION}" \
                            -t "\$IMAGE:latest" \
                            .

                        docker push "\$IMAGE:${ARTIFACT_VERSION}"
                        docker push "\$IMAGE:latest"
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
