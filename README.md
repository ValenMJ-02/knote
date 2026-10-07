# Knote – Spring Boot + MongoDB + MinIO en Kubernetes (Minikube)

App de notas (Markdown + imágenes). Las notas se guardan en **MongoDB** y las imágenes en **MinIO**,
por lo que la app es *stateless* y puede escalarse a varias réplicas.

## Ejecutar en local
```bash
docker compose up -d                      # MongoDB + MinIO
mvn -Dminio.access.key=mykey -Dminio.secret.key=mysecret spring-boot:run
# http://localhost:8080
docker compose down                       # al terminar
```

## Imagen Docker
```bash
docker build -t <usuario>/knote:2.0.0 .
docker push <usuario>/knote:2.0.0
```

## Kubernetes (Minikube)
```bash
# editar kube/knote.yaml y poner tu usuario de Docker Hub en la imagen
minikube start
kubectl apply -f kube
kubectl get pods --watch
minikube service knote --url
kubectl scale --replicas=10 deployment/knote
kubectl delete -f kube                    # limpiar
```
