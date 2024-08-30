#!/bin/bash

source config.sh

# Install java.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat $DIR/instance.dns) $cmd

# Upload aws config file
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $DIR/config.sh ec2-user@$(cat $DIR/instance.dns):

cd $DIR/../worker
mvn clean package
cd ..
tar -czvf worker.tar.gz worker

# Install worker code.
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH worker.tar.gz ec2-user@$(cat $DIR/instance.dns):

# Untar worker code.
cmd="tar xfz worker.tar.gz worker && rm worker.tar.gz"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat $DIR/instance.dns) $cmd

# Setup web server to start on instance launch.
cmd="grep -q SNAPSHOT /etc/rc.local || (echo \"source /home/ec2-user/config.sh && cd /home/ec2-user/worker && java -cp webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:javassist/target/javassist-1.0.0-SNAPSHOT-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv,imageproc,boofcv:output pt.ulisboa.tecnico.cnv.webserver.WebServer &> /tmp/log.txt\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local)"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat $DIR/instance.dns) $cmd
