properties([
    parameters([
        string(
            defaultValue: '',
            name: 'VM_NAME', 
            description: 'Name of the VM to check in'
        ),
        validatingString(
            defaultValue: '10.176.117.12',
            name: 'VM_IP',
            regex: /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/,
            description: 'IP of the VM to check in',
        ),
        booleanParam(
            name: 'CONFIRM_RUN',
            defaultValue: false,
            description: 'Check only if you are sure to check in the VM. All changes will be lost.'
        )
    ])
])

pipeline {
    agent { 
        label "master" 
    }
    stages {
        stage('Login to the Azure account') {
            when {
                expression { params.CONFIRM_RUN }
            }
            steps{
                withCredentials([azureServicePrincipal("az-jenkins-sp-dev")]) {
                    //Login to the Azure subscription using service principal
                    sh "az login --service-principal --username ${AZURE_CLIENT_ID} --password '${AZURE_CLIENT_SECRET }' --tenant '${AZURE_TENANT_ID}'"
                }   
            }
        }
        stage('Check if user has permission to check-in') {
            when {
                expression { params.CONFIRM_RUN }
            }
            steps{
                script {
                    //Get the public key from the user
                    def inputFile = input message: 'Upload public key to check if authorized', parameters: [file(name: "${WORKSPACE}/client.pub")]
                }
                //Download the admin key from the keyvault
                sh "az keyvault secret download --file vm1-admin-key --vault-name hub-ivt-dev-vault --name vm1-admin-key"
                sh "chmod 600 vm1-admin-key"
                //Copy the users public key and script to check if user exists
                sh "scp -i vm1-admin-key check-user-exists.sh client.pub azurevm@${params.VM_IP}:/tmp/"
                script {
                    //Check if user public key already exists on the destination server
                    KEY_EXISTS = sh(returnStatus: true, script: "ssh -i vm1-admin-key azurevm@${params.VM_IP} 'chmod a+x /tmp/check-user-exists.sh;sh /tmp/check-user-exists.sh'")
                }
            }
        }
        stage('Check-In and Reset') {
            when {
                expression { params.CONFIRM_RUN && "$KEY_EXISTS" == '0' }
            }
            steps {
                echo "User has access to VM. Checking-In"
                sh "scp -i vm1-admin-key remove-user.sh azurevm@${params.VM_IP}:/tmp/"
                //If key exists, remove from authorized users list
                sh "ssh -i vm1-admin-key azurevm@${params.VM_IP} 'chmod a+x /tmp/remove-user.sh;sh /tmp/remove-user.sh'"
                //Clear the files copied on the destination
                sh "ssh -i vm1-admin-key azurevm@${params.VM_IP} 'rm -rf /tmp/check-user-exists.sh /tmp/client.pub /tmp/remove-user.sh'"
                //Update the tag denoting the VM has been checked-in
                sh "az vm update --resource-group administrative-vms-rg --name ${params.VM_NAME} --set tags.status=checked-in"
            }
        }
        stage('User does not have permissions to check-in') {
            when {
                expression { params.CONFIRM_RUN && "$KEY_EXISTS" == '1' }
            }
            steps {
                //If key does not exist, that means user does not have access and hence permission to check in. Clear the scripts copied on the destination VM
                sh "ssh -i vm1-admin-key azurevm@${params.VM_IP} 'rm -rf /tmp/check-user-exists.sh /tmp/client.pub'"
                echo "User does not have access to VM. Cannot check-in"
            }
        }
    }
}