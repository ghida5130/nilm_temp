pipeline {
    agent none
    options {
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
    }
    parameters {
        booleanParam(name: 'ENABLE_CD', defaultValue: false,
            description: '초기 EC2/인증서/Credentials 준비 후 master 배포 활성화')
        string(name: 'IMAGE_REPOSITORY', defaultValue: 'docker.io/leejeongmin24/on-maum',
            description: 'Docker Hub Private repository: docker.io/account/repository (no tag)')
        string(name: 'FRONTEND_ENV_CREDENTIAL', defaultValue: '',
            description: 'Optional Secret file credential for public Vite build settings')
    }
    stages {
        stage('CI') {
            agent { label 'ci' }
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.RELEASE_ID = "${env.IMAGE_TAG}-b${env.BUILD_NUMBER}"
                    env.IMAGE_REPOSITORY = params.IMAGE_REPOSITORY?.trim() ?: 'docker.io/leejeongmin24/on-maum'
                    if (!(env.IMAGE_REPOSITORY ==~ /docker\.io\/[a-z0-9]+(?:[._-][a-z0-9]+)*\/[a-z0-9]+(?:[._-][a-z0-9]+)*/)) {
                        error('IMAGE_REPOSITORY must be docker.io/account/repository without a tag')
                    }
                }
                sh '''
                    RUN_COMPOSE_TESTS=1 python3 -m unittest discover -s infrastructure/scripts/tests -v
                    for target in local ec2-a ec2-b; do
                        docker compose --env-file infrastructure/$target/.env.example -f infrastructure/$target/compose.yaml config --quiet
                    done
                '''
                script {
                    // Feature/PR builds never receive production credentials.
                    if (env.BRANCH_NAME == 'master' && params.FRONTEND_ENV_CREDENTIAL.trim()) {
                        withCredentials([file(credentialsId: params.FRONTEND_ENV_CREDENTIAL.trim(), variable: 'FRONTEND_ENV')]) {
                            sh 'bash infrastructure/scripts/build.sh'
                        }
                    } else {
                        sh 'bash infrastructure/scripts/build.sh'
                    }
                }
                stash name: 'deploy-config', includes: 'infrastructure/ec2-*/compose.yaml,infrastructure/scripts/**,infrastructure/nginx/*.template,infrastructure/keycloak/*.json,infrastructure/postgres/*.sql,infrastructure/mqtt/config/mosquitto.production.conf', excludes: '**/__pycache__/**'
                script {
                    if (env.BRANCH_NAME == 'master' && params.ENABLE_CD) {
                        withCredentials([usernamePassword(credentialsId: 'registry-login',
                            usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                            sh 'bash infrastructure/scripts/with-registry.sh bash infrastructure/scripts/push.sh'
                        }
                        stash name: 'release-manifest', includes: 'release.json'
                        archiveArtifacts artifacts: 'release.json', fingerprint: true
                    }
                }
            }
        }
        stage('Pull A images') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-a' }
            steps {
                unstash 'deploy-config'
                unstash 'release-manifest'
                withCredentials([usernamePassword(credentialsId: 'registry-login',
                    usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                    sh 'bash infrastructure/scripts/with-registry.sh python3 infrastructure/scripts/release.py pull --target a'
                }
            }
        }
        stage('Pull B images') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-b' }
            steps {
                unstash 'deploy-config'
                unstash 'release-manifest'
                withCredentials([usernamePassword(credentialsId: 'registry-login',
                    usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                    sh 'bash infrastructure/scripts/with-registry.sh python3 infrastructure/scripts/release.py pull --target b'
                }
            }
        }
        stage('Prepare A') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-a' }
            steps {
                unstash 'deploy-config'
                withCredentials([file(credentialsId: 'ec2-a-runtime-env', variable: 'RUNTIME_ENV')]) {
                    unstash 'release-manifest'
                    sh 'bash infrastructure/scripts/prepare.sh a'
                }
            }
        }
        stage('Prepare B') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-b' }
            steps {
                unstash 'deploy-config'
                withCredentials([file(credentialsId: 'ec2-b-runtime-env', variable: 'RUNTIME_ENV')]) {
                    unstash 'release-manifest'
                    sh 'bash infrastructure/scripts/prepare.sh b'
                }
            }
        }
        stage('Deploy B base') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-b' }
            steps {
                unstash 'deploy-config'
                withCredentials([usernamePassword(credentialsId: 'registry-login',
                    usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                    sh 'bash infrastructure/scripts/with-registry.sh bash infrastructure/scripts/deploy.sh b-base'
                }
            }
        }
        stage('Deploy A and verify HTTP') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-a' }
            steps {
                unstash 'deploy-config'
                withCredentials([usernamePassword(credentialsId: 'registry-login',
                    usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                    sh 'bash infrastructure/scripts/with-registry.sh bash infrastructure/scripts/deploy.sh a'
                }
                sh 'python3 infrastructure/scripts/verify-http.py'
            }
        }
        stage('Deploy Bridge and verify pipeline') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-b' }
            steps {
                unstash 'deploy-config'
                sh 'bash infrastructure/scripts/deploy.sh b-bridge'
            }
        }
        stage('Record success A') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-a' }
            steps {
                unstash 'deploy-config'
                sh 'python3 infrastructure/scripts/release.py success --manifest /opt/nilm/release.json'
            }
        }
        stage('Record success B') {
            when { beforeAgent true; allOf { branch 'master'; expression { params.ENABLE_CD } } }
            agent { label 'ec2-b' }
            steps {
                unstash 'deploy-config'
                sh 'python3 infrastructure/scripts/release.py success --manifest /opt/nilm/release.json'
            }
        }
    }
}
