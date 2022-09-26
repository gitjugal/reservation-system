pipeline {
    agent { 
        label "master" 
    }

    stages {
        stage('Login to the Azure account') {
            steps{
                withCredentials([azureServicePrincipal("az-jenkins-sp-dev")]) {
                    //Login to the Azure subscription using service principal
                    sh "az login --service-principal --username ${AZURE_CLIENT_ID} --password '${AZURE_CLIENT_SECRET }' --tenant '${AZURE_TENANT_ID}'"
                }   
            }
        }
        stage('Check if VM available') {
            steps {
                script  {
                    //Create a json containing the list of VMs available for checkout  
                    VM_LIST = sh(returnStdout: true, script: 'az vm list-ip-addresses --ids $(az resource list --resource-group administrative-vms-rg --query "[?type==\'Microsoft.Compute/virtualMachines\' && tags.status == \'checked-in\'].id" --output tsv) --query "[].{Name:virtualMachine.name,IP:virtualMachine.network.privateIpAddresses[0]}" 2>nul | tee vm-list.json')
                }
            }
        }
        stage('Check-out VM if available') {
            when {
                expression {
                    !VM_LIST.isEmpty()
                }
            }
            steps {
                script {
                    //Get the first VM from the list of available VMs
                    VM_IP = sh(returnStdout: true, script: 'cat vm-list.json | jq .[0].IP').trim()
                    VM_NAME = sh(returnStdout: true, script: 'cat vm-list.json | jq .[0].Name').trim()
                }
                echo "Provisioning VM $VM_NAME with IP $VM_IP from list $VM_LIST"
                script {
                    //Get the public key from the user
                    def inputFile = input message: 'Upload public key for SSH', parameters: [file(name: "${WORKSPACE}/client.pub")]
                }
                //Download the admin key from the keyvault to be used to copy users public key
                sh "az keyvault secret download --file vm1-admin-key --vault-name hub-ivt-dev-vault --name vm1-admin-key"
                sh "chmod 600 vm1-admin-key"
                //Add the users public key to the list of authorized users on destination VM
                sh "ssh-copy-id -f -i ${WORKSPACE}/client.pub -o \"IdentityFile vm1-admin-key\"  -o StrictHostKeyChecking=no azurevm@$VM_IP"
                //Update the allocated VMs tag denoting it has been checked-out. 
                sh "az vm update --resource-group administrative-vms-rg --name $VM_NAME --set tags.status=checked-out"
                echo "Allocated VM $VM_NAME with IP $VM_IP. Use private key to login"
            }
        }
        stage('Retry Later') {
            when {
                expression {
                    //If no VM available, notify user to try later
                    VM_LIST.isEmpty()
                }
            }
            steps {
                echo "No VM available currently, please try later"
            }
        }
    }
    post {
        always {
            cleanWs()
        }
    }
}
