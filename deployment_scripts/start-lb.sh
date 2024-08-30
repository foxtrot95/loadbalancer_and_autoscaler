#!/bin/bash

source config.sh 

# Run Load Balancer 
cmd="source /home/ec2-user/config.sh && cd /home/ec2-user/loadbalancer_autoscaler && java -cp target/loadbalancer_autoscaler-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.webserver.WebServer > /tmp/log.txt"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat $DIR/instance.dns) $cmd
