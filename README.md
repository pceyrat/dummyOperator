# Dummy Operator

## Introduction
This project is followed by the medium post [Kubernetes Dummy Operator in Java](). Go check it out for a more detailed intro and to see a demonstration.

## Operator goals
1. manage the custom resources
1. create deployments with custom resource information (using the busybox image for the pods)
1. inside the pods launched by the deployment run the command
"/bin/sh -c /bin/echo "$quote\n$extra"; /bin/sleep $sleep".

You can check if the operator is up by checking its health with the command

```bash
http://localhost:8080/dummy/health
```

At the moment if fetches all Custom Resource Definitions and looks for the Dummy one. If it does not find it it reports as down.

## How to launch

Before launching you need kubernetes up and running and it also needs to have the Dummy custom resource (which you can create by doing "kubectl apply -f yaml/dummycrd.yaml"). Afterwards, you can launch it by executing "mvn spring-boot:run"

Another alternative to launch is by building a docker image with the command
```bash
mvn spring-boot:build-image
# or using the Dockerfile and running the command -> docker build -t operator:0.0.1-SNAPSHOT --build-arg VERSION=0.0.1-SNAPSHOT .
``` 
And then launch a pod with that image
```bash
kubectl run myoperator -it --rm --image=operator:0.0.1-SNAPSHOT
``` 

If you generate the image you can also launch the operator by creating the objects in the file
[`myoperator.yaml`](yaml/myoperator.yaml) which will create a deployment and a service with NodePort. If you use the file as is you will need to create the objects in the [`setup.yaml`](yaml/setup.yaml) file as well. Beware that if you are using minikube you might need to set it to use local docker images. 

If you initiated the pod with myoperator.yaml file you can reach it by using the command
```bash
curl -s 127.0.0.1:$(kubectl get svc myoperator -o jsonpath='{.spec.ports[].nodePort}')/dummy/info | jq .
``` 
*note: the jq executable is just to make it look prettier and as far as I know it is not installed by default*

## Inspiration
* [Write a simple Kubernetes Operator in Java using the Fabric8 Kubernetes Client](https://developers.redhat.com/blog/2019/10/07/write-a-simple-kubernetes-operator-in-java-using-the-fabric8-kubernetes-client)
* [Fabric8 Kubernetes Java Client Cheat Sheet](https://github.com/fabric8io/kubernetes-client/blob/master/doc/CHEATSHEET.md)
