try {
    timeout(time: 20, unit: 'MINUTES') {
        def appName = "springboot-ws-dockerfile"
        def project = ""
        def ocCmd = "oc " +
                " --server=https://openshift.default.svc.cluster.local" +
                " --certificate-authority=/run/secrets/kubernetes.io/serviceaccount/ca.crt"
        def mvnCmd = "mvn"

        node {
            stage("Initialize") {
                def token = sh(script: 'cat /var/run/secrets/kubernetes.io/serviceaccount/token', returnStdout: true)
                ocCmd = ocCmd + " --token=" + token
                project = env.PROJECT_NAME
                ocCmd = ocCmd + " -n ${project}"
            }
        }
        node("maven-jos-openjdk8") {
            stage("Git Checkout") {
                git branch: 'develop', url: 'ssh://git@git.belastingdienst.nl:7999/~wolfj09/os-fase3-springboot-ws.git'
            }
            stage("Compile/Test/Build JAR") {
                sh "${mvnCmd} clean package -Popenshift"
            }g
            stage("Build Image") {
                sh "oc start-build ${appName} --from-file=target/fatjar.jar -n ${project}"
                openshiftVerifyBuild bldCfg: "${appName}", namespace: project, waitTime: '5', waitUnit: 'min'
            }
            stage("Deploy Image") {
                openshiftDeploy deploymentConfig: appName, namespace: project
                openshiftVerifyDeployment depCfg: "${appName}", namespace: project, waitTime: '2', waitUnit: 'min'
            }

            stage("Webservice Testing") {
                def appURL = sh(script: ocCmd + " get routes -l app=${appName} -o template --template {{range.items}}{{.spec.host}}{{end}}", returnStdout:true)
                sleep 5
                sh(script: "curl ${appURL}/health | grep 'UP'")
            }
        }
    }
} catch (err) {
    echo "in catch block"
    echo "Caught: ${err}"
    currentBuild.result = 'FAILURE'
    throw err
}