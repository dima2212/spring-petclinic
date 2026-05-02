node {
    properties([
        parameters([
            [$class: 'ChoiceParameter',
                name: 'IMAGE_TAG',
                description: 'Select the image tag to promote',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    $class: 'GroovyScript',
                    fallbackScript: [
                        classpath: [], sandbox: true,
                        script: 'return ["ERROR: could not fetch tags"]'
                    ],
                    script: [
                        classpath: [], sandbox: true,
                        script: '''
                            try {
                                def cmd = ["curl", "-s",
                                    "https://hub.docker.com/v2/repositories/pacman2212/petclinic/tags?page_size=10&ordering=last_updated"]
                                def output = cmd.execute().text
                                def json = new groovy.json.JsonSlurper().parseText(output)
                                return json.results
                                    .collect { it.name }
                                    .findAll { it != "latest" }
                            } catch (Exception e) {
                                return ["ERROR: ${e.message}"]
                            }
                        '''
                    ]
                ]
            ],
            [$class: 'ChoiceParameter',
                name: 'TARGET_ENV',
                description: 'Target environment',
                choiceType: 'PT_SINGLE_SELECT',
                filterable: false,
                script: [
                    $class: 'GroovyScript',
                    fallbackScript: [classpath: [], sandbox: true, script: 'return ["dev"]'],
                    script: [classpath: [], sandbox: true, script: 'return ["dev", "staging", "prod"]']
                ]
            ]
        ])
    ])
}

pipeline {
    agent any
    stages {
        stage('Approval') {
            when {
                expression { params.TARGET_ENV == 'prod' }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input(
                        message: "Promote pacman2212/petclinic:${params.IMAGE_TAG} to PROD?",
                        ok: 'Approve',
                        submitter: 'ops-team'
                    )
                }
            }
        }

        stage('Update GitOps Repo') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'git-token', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                    script {
                        sh """
                            git clone https://\${GIT_USER}:\${GIT_TOKEN}@github.com/dima2212/gitops
                            cd gitops

                            git config user.email "jenkins@local.ci"
                            git config user.name  "Jenkins CI"

                            sed -i "s/tag:.*/tag: \\"${params.IMAGE_TAG}\\"/" charts/petclinic/values-${params.TARGET_ENV}.yaml

                            if git diff --quiet; then
                                echo "No change — ${params.IMAGE_TAG} already in values-${params.TARGET_ENV}.yaml"
                            else
                                git add charts/petclinic/values-${params.TARGET_ENV}.yaml
                                git commit -m "chore(${params.TARGET_ENV}): promote petclinic to ${params.IMAGE_TAG}"
                                git push origin main
                            fi
                        """
                    }
                }
            }
        }
    }

    post {
        cleanup {
            cleanWs()
        }
    }
}
