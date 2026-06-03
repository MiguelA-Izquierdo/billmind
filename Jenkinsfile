pipeline {
    agent { label 'docker-enabled' }

    tools {
            jdk 'java-21'
        }

    environment {
        APP_NAME = 'billmind'
    }

    parameters {
        string(name: 'REGISTRY', defaultValue: '192.168.1.100:5000', description: 'Registry de imágenes')
    }

    stages {

        stage('Prepare') {
            steps {
                script {
                    def tag
                    if (env.TAG_NAME) {
                        tag = env.TAG_NAME
                    } else if (env.BRANCH_NAME == 'main') {
                        tag = 'latest'
                    } else if (env.BRANCH_NAME == 'develop') {
                        tag = 'beta'
                    } else if (env.BRANCH_NAME.startsWith('feature/')) {
                        tag = 'alpha'
                    } else {
                        tag = env.BRANCH_NAME.replaceAll('/', '-')
                    }

                    env.DOCKER_TAG = tag
                    env.IS_MAIN    = (env.BRANCH_NAME == 'main').toString()

                    echo "Branch: ${env.BRANCH_NAME} | Docker tag: ${env.DOCKER_TAG}"
                }
            }
        }

        stage('Tests') {
            steps {
                sh """
                    chmod +x mvnw
                    ./mvnw test
                """
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
                success {
                    script {
                        if (env.IS_MAIN == 'true') {
                            jacoco(
                                execPattern:   'target/*.exec',
                                classPattern:  'target/classes',
                                sourcePattern: 'src/main/java'
                            )
                        }
                    }
                }
            }
        }

        stage('Build & Push') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    branch pattern: 'feature/*', comparator: 'GLOB'
                    buildingTag()
                }
            }
            steps {
                script {
                    def image = "${params.REGISTRY}/${APP_NAME}:${env.DOCKER_TAG}"

                    if (env.TAG_NAME) {
                        withCredentials([usernamePassword(
                            credentialsId: 'local-registry-creds-DOCKER',
                            usernameVariable: 'REG_USER',
                            passwordVariable: 'REG_PASS'
                        )]) {
                            def status = sh(
                                script: "curl -s -o /dev/null -w '%{http_code}' -u \${REG_USER}:\${REG_PASS} http://${params.REGISTRY}/v2/${APP_NAME}/manifests/${env.DOCKER_TAG}",
                                returnStdout: true
                            ).trim()
                            if (status == '200') {
                                error "La imagen ${image} ya existe en el registry — los tags git son inmutables."
                            }
                        }
                    }

                    docker.withRegistry("http://${params.REGISTRY}", 'local-registry-creds-DOCKER') {
                        sh """
                            chmod +x mvnw
                            ./mvnw package -DskipTests -Dmaven.test.skip=true
                            docker build -t ${image} .
                            docker push ${image}
                        """
                    }
                }
            }
        }

        stage('Backup') {
            when {
                anyOf {
                    branch 'main'
                    buildingTag()
                }
            }
            steps {
                script {
                    def image = "${params.REGISTRY}/${APP_NAME}:${env.DOCKER_TAG}"
                    def path  = env.BACKUP_PATH ?: "/opt/docker-backups/${APP_NAME}"
                    def ts    = new Date().format('yyyyMMdd-HHmm')
                    sh """
                        mkdir -p ${path}
                        docker save ${image} \
                            | gzip > ${path}/backup-${env.DOCKER_TAG}-${ts}.tar.gz
                        docker image prune -f
                    """
                }
            }
        }
    }

    post {
        failure {
            script {
                def image = "${params.REGISTRY}/${APP_NAME}:${env.DOCKER_TAG ?: 'unknown'}"
                sh "docker rmi ${image} || true"
                echo "Pipeline failed — image ${image} removed"
            }
        }
        always {
            cleanWs()
        }
    }
}