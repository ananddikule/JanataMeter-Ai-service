pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        skipDefaultCheckout(true)
    }

    environment {
        DOCKER_IMAGE = 'docker.io/ananddikule/janmeter-ai-service'
        K8S_NAMESPACE = 'default'
        K8S_DEPLOYMENT = 'janmeter-ai-service'
        K8S_CONTAINER = 'janmeter-ai-service'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    def shortSha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${shortSha}"
                    env.FULL_IMAGE = "${env.DOCKER_IMAGE}:${env.IMAGE_TAG}"
                    echo "Image: ${env.FULL_IMAGE}"
                }
            }
        }

        stage('Check Environment') {
            steps {
                sh '''
                    set -eu
                    docker --version
                    kubectl --kubeconfig=/dev/null version --client
                    git --version
                    java -version
                    test -f Dockerfile
                    test -f k8s/jenkins-deploy.sh
                    bash -n k8s/jenkins-deploy.sh
                '''
            }
        }

        stage('Test') {
            steps {
                sh './gradlew --no-daemon test'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build --pull -t "$FULL_IMAGE" .'
            }
        }

        stage('Push Docker Image') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_TOKEN'
                )]) {
                    sh '''
                        set -eu
                        set +x
                        export DOCKER_CONFIG
                        DOCKER_CONFIG="$(mktemp -d)"
                        trap 'rm -rf "$DOCKER_CONFIG"' EXIT
                        printf '%s' "$DOCKER_TOKEN" | docker login \
                          --username "$DOCKER_USER" --password-stdin
                        docker push "$FULL_IMAGE"
                    '''
                }
            }
        }

        stage('Deploy to Shared Kubernetes Cluster') {
            steps {
                withCredentials([
                    file(credentialsId: 'janmeter-kubeconfig', variable: 'KUBECONFIG'),
                    string(credentialsId: 'openai-api-key', variable: 'OPENAI_API_KEY'),
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_TOKEN'
                    )
                ]) {
                    sh '''
                        set -eu
                        set +x

                        kubectl -n "$K8S_NAMESPACE" create secret docker-registry dockerhub-registry \
                          --docker-server=https://index.docker.io/v1/ \
                          --docker-username="$DOCKER_USER" \
                          --docker-password="$DOCKER_TOKEN" \
                          --dry-run=client -o yaml | kubectl apply -f -

                        kubectl -n "$K8S_NAMESPACE" create secret generic janmeter-ai-secrets \
                          --from-literal=OPENAI_API_KEY="$OPENAI_API_KEY" \
                          --dry-run=client -o yaml | kubectl apply -f -

                        export IMAGE="$FULL_IMAGE"
                        bash k8s/jenkins-deploy.sh
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'JanMeter AI Service deployment completed successfully.'
        }
        failure {
            script {
                try {
                    withCredentials([file(credentialsId: 'janmeter-kubeconfig', variable: 'KUBECONFIG')]) {
                        sh '''
                            set +e
                            mkdir -p k8s-diagnostics
                            kubectl -n "$K8S_NAMESPACE" get pods -l app.kubernetes.io/name=janmeter-ai-service -o wide \
                              > k8s-diagnostics/pods.txt 2>&1
                            kubectl -n "$K8S_NAMESPACE" describe deployment janmeter-ai-service \
                              > k8s-diagnostics/deployment.txt 2>&1
                            kubectl -n "$K8S_NAMESPACE" logs deployment/janmeter-ai-service \
                              --all-pods=true --all-containers=true --prefix=true --tail=300 --max-log-requests=20 \
                              > k8s-diagnostics/logs.txt 2>&1
                        '''
                    }
                } catch (Exception e) {
                    echo "Unable to collect Kubernetes diagnostics: ${e.message}"
                }
            }
        }
        always {
            archiveArtifacts(artifacts: 'k8s-diagnostics/*.txt', allowEmptyArchive: true)
        }
    }
}
