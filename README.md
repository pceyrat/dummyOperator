# Getting Started


### Operator goals
This operator will manage the custom resources and it will create and deployment and run the command
"/bin/sh -c /bin/echo "$quote\n$extra"; /bin/sleep $sleep" on a busybox.

To see if the operator is up you can use the link to check its health http://localhost:8080/dummy/health. At the moment if fetches all Custom Resource Definitions and looks for one that has the Dummy one. If it does not find it it reports it as down.

You can launch it running "mvn exec:java"

You can also build an image using the Dockerfile with the command "docker build -t myoperator:1.0.0 --build-arg VERSION=1.0.0 ." for example and then execute the image in kubernetes "kubectl run myoperator -it --rm --image=myoperator:1.0.0"

Alternatively you can create the kubernetes objects in the file `yaml/myoperator.yaml` which will create a deployment with a service with NodePort type. After the pod is initiated you can execute the command to see it in action "curl -s localhost:$(kubectl get svc myoperator -o jsonpath='{.spec.ports[].nodePort}')/dummy/info | jq ." (note: the jq executable is just to make it look prettier and normally is not installed by default)


### Inspiration

* [Write a simple Kubernetes Operator in Java using the Fabric8 Kubernetes Client](https://developers.redhat.com/blog/2019/10/07/write-a-simple-kubernetes-operator-in-java-using-the-fabric8-kubernetes-client)
* [Fabric8 Kubernetes Java Client Cheat Sheet](https://github.com/fabric8io/kubernetes-client/blob/master/doc/CHEATSHEET.md)
