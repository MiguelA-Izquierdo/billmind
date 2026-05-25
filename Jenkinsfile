pipeline {
    agent { label 'docker-enabled' }

    parameters {
        string(name: 'BRANCH_TO_BUILD', defaultValue: 'main', description: 'Branch to build')
    }

    environment {
        APP_NAME = 'billmind'
    }

    stages {

        stage('Checkout') {
            steps {
                script {
                    def raw   = params.BRANCH_TO_BUILD ?: 'main'
                    def clean = raw
                        .replaceAll('origin/', '')
                        .replaceAll('refs/heads/', '')
                        .replaceAll('refs/tags/', '')

                    echo "Building branch: ${clean}"

                    checkout([$class: 'GitSCM',
                        branches: [[name: "*/${clean}"]],
                        userRemoteConfigs: scm.userRemoteConfigs,
                        extensions: scm.extensions
                    ])

                    def tag
                    if (env.TAG_NAME) {
                        tag = env.TAG_NAME
                    } else if (clean == 'main') {
                        tag = 'latest'
                    } else if (clean == 'develop') {
                        tag = 'beta'
                    } else if (clean.startsWith('feature/')) {
                        tag = 'alpha'
                    } else {
                        tag = clean.replaceAll('/', '-')
                    }

                    env.DOCKER_TAG = tag
                    env.IS_MAIN    = (clean == 'main').toString()

                    echo "Docker tag: ${env.DOCKER_TAG} | is main: ${env.IS_MAIN}"
                }
            }
        }

        stage('Tests') {
            steps {
                script {
                    def tag = env.DOCKER_TAG
                    echo "Running tests for tag: ${tag}"
                    sh """
                        chmod +x mvnw
                        ./mvnw test
                    """
                }
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

        stage('Build & Dockerize') {
            steps {
                script {
                    def tag = env.DOCKER_TAG
                    sh """
                        chmod +x mvnw
                        ./mvnw package -DskipTests -Dmaven.test.skip=true
                        docker build -t ${APP_NAME}:${tag} .
                    """
                }
            }
        }

        stage('Backup') {
            steps {
                script {
                    def tag  = env.DOCKER_TAG
                    def path = env.BACKUP_PATH ?: "/opt/docker-backups/${APP_NAME}"
                    def ts   = new Date().format('yyyyMMdd-HHmm')
                    sh """
                        mkdir -p ${path}
                        docker save ${APP_NAME}:${tag} \
                            | gzip > ${path}/backup-${tag}-${ts}.tar.gz
                        docker image prune -f
                    """
                }
            }
        }
    }

    post {
        failure {
            script {
                def tag = env.DOCKER_TAG ?: 'unknown'
                sh "docker rmi ${APP_NAME}:${tag} || true"
                echo "Pipeline failed — image ${APP_NAME}:${tag} removed"
            }
        }
        always {
            cleanWs()
        }
    }
}