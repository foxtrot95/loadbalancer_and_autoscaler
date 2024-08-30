#!/bin/bash

source config.sh

# Install java.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat $DIR/instance.dns) $cmd

# Upload aws config file
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $DIR/config.sh ec2-user@$(cat $DIR/instance.dns):
exit

cd $DIR/../lb_as/loadbalancer_autoscaler
mvn clean package
cd ..
tar -czvf lb.tar.gz loadbalancer_autoscaler

# Install worker code.
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH lb.tar.gz ec2-user@$(cat $DIR/instance.dns):

# Untar worker code.
cmd="tar xfz lb.tar.gz loadbalancer_autoscaler && rm lb.tar.gz"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat $DIR/instance.dns) $cmd
