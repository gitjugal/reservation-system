# Reservation System

The reservation system allows users to borrow/check-out VMs(Linux) from a list of Azure Cloud VMs. Once they are done using it, they can check-in the VM back.  

## System Architecture

![Alt text](ReservationSystem_Arch.jpg?raw=true "Reservation System Architecture")

## Pre-requisites

The system depends on the following pre-requisites - 
* The Azure VMs have a tag associated called as status=checked-in or status=checked-out to depict if the VM is checked out for use or checked in and available for use. Initially all the VMs will have tag as status=checked-in
* All the VMs should have an initial admin key on them, the private key for which will be copied to the Azure Keyvault as a secret
* An Azure service principal to allow CLI calls to the platform for managing the VM instances.
* The user must have a SSH key pair to use for login to the VM.

## Usage

### VM Check-Out 
 
 * For VM check-out, the user has to run the checkout-vm pipeline job.
 ![Alt text](results/checkout-vm-1.png?raw=true "checkout vm job")
 * In the first stage, it will login to Azure cloud using the service principal configured in the jenkins credential manager.
 * In the next stage, it will run some CLI queries to fetch the list of VMs which are checked-in and available for use. It creates a json with the list of avaiable VMs. 
 * If VMs are available, it will automatically choose the first from the list for allocation. 
 ![Alt text](results/checkout-vm-2.png?raw=true "create inventory and choose")
 * It then prompts for an input, wherein the user must upload the public key from the generated SSH key pair
 ![Alt text](results/checkout-vm-3.png?raw=true "upload public key")
 * The public key is copied to the authorized hosts file on the destination VM, and the VM details are shared on the console for user. User can then use their private key to securely login to the VM. 
 ![Alt text](results/checkout-vm-4.png?raw=true "vm details")
 * Finally, the job updates the tag on the allocated VM to mark it as checked out. 
 * If no VM is available for checkout, the user is notified to try again later.

### VM Check-In

 * For VM check-in, the user has to run the checkout-in pipeline job.
 * The job requires input parameters in the form of VM Name and VM IP to check-in. 
 ![Alt text](results/checkin-vm-1.png?raw=true "checkin vm job")
 * In the first stage, it will login to Azure cloud using the service principal configured in the jenkins credential manager.
 * It will then promput for action, wherein the user must upload the public key they initially used to checkout the VM. This is used to validate if the user is same as the one who checked out the VM.
 * If the user matches, then the public key is removed from the authorized hosts file.
 ![Alt text](results/checkin-vm-2.png?raw=true "remove user if authorized")
 * Ultimately, it will update the tag on checked in VM to status=checked-in. This depicts that the VM is added to the pool of VMs which are available. 
 * If the user public key does not match the one on VM, then the VM cannot be checked-in. 