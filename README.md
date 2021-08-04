# Getting Started

## Operator goals
This operator will manage the custom resources and it will create and deployment and run the command
"/bin/sh -c /bin/echo "$quote\n$extra"; /bin/sleep $sleep" on a busybox.

To see if the operator is up you can use the link to check its health http://localhost:8080/dummy/health. At the moment if fetches all Custom Resource Definitions and looks for one that has the Dummy one. If it does not find it it reports it as down.

You can launch it running "mvn compile exec:java" but for that you need kubernetes up and running and do "kubectl create -f yaml/dummycrd.yaml"

You can also build an docker image with the command "mvn spring-boot:build-image" and then execute the image in kubernetes "kubectl run myoperator -it --rm --image=operator:0.0.1-SNAPSHOT"

Alternatively you can create the kubernetes objects using the file `yaml/myoperator.yaml` which will create a deployment with a service with NodePort type but before you need to create the objects in `yaml/setup.yaml`. Beware that it was set using the docker desktop and if you are using minikube you might need to set minikube to use local docker images. After the pod is initiated you can execute the command to see it in action "curl -s localhost:$(kubectl get svc myoperator -o jsonpath='{.spec.ports[].nodePort}')/dummy/info | jq ." (note: the jq executable is just to make it look prettier and as far as I know it is not installed by default)


## Inspiration

* [Write a simple Kubernetes Operator in Java using the Fabric8 Kubernetes Client](https://developers.redhat.com/blog/2019/10/07/write-a-simple-kubernetes-operator-in-java-using-the-fabric8-kubernetes-client)
* [Fabric8 Kubernetes Java Client Cheat Sheet](https://github.com/fabric8io/kubernetes-client/blob/master/doc/CHEATSHEET.md)
