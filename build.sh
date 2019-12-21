cd services
sbt publishLocal
cd ..

mkdir account/lib
mkdir account-sync/lib
mkdir transfer/lib

cp services/target/scala-2.12/services_2.12-0.1.jar account/lib
cp services/target/scala-2.12/services_2.12-0.1.jar account-sync/lib
cp services/target/scala-2.12/services_2.12-0.1.jar transfer/lib

cd api/src/protobuf/
chmod +x gen.sh
./gen.sh
cd ../../..

cd account
sbt docker:publishLocal
cd ..

cd account-sync
sbt docker:publishLocal
cd ..

cd transfer
sbt docker:publishLocal
cd ..

docker build ./api -t ledger-api:0.1