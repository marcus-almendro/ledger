# Ledger implementation using CQRS pattern and Actor Model with Akka
This project is an implementation of a general ledger showing how CQRS and Actor Model works.

### Architecture
![Architecture](link-to-image)

### Components used
* Akka (Cluster Sharding, Streams)
* GRPC
* Eventstore
* Node.js
* RabbitMQ
* Cassandra
* Nginx

### Steps to run:
1) Clone repository
2) build.sh (linux) or build.bat (windows)
3) docker-compose up
4) run postman collection